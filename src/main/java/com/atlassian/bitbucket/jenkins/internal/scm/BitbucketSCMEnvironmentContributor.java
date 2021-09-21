package com.atlassian.bitbucket.jenkins.internal.scm;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.IOException;
import java.util.Objects;

@Extension
public class BitbucketSCMEnvironmentContributor extends EnvironmentContributor {

    public void buildEnvironmentFor(@NonNull Job job, @NonNull EnvVars envs, @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (job instanceof AbstractProject) {
            // Case 1 - Freestyle job
            SCM scm = ((AbstractProject<?, ?>) job).getScm();
            if (!(scm instanceof BitbucketSCM)) {
                return;
            }
            ((BitbucketSCM) scm).getBitbucketSCMRepository().buildEnvironment(envs);
            return;
        }
        if (job instanceof WorkflowJob) {
            // Case 2 - Not a freestyle job. Proceed to inspect SCM on item
            SCM scm = ((WorkflowJob) job).getTypicalSCM();
            if (scm == null) {
                return;
            }
            if (scm instanceof BitbucketSCM) {
                //case 2 - bb_checkout step in the script (pipeline or groovy)
                ((BitbucketSCM) scm).getBitbucketSCMRepository().buildEnvironment(envs);
                return;
            }
            ItemGroup<?> parent = getJobParent(job);
            if (parent instanceof MultiBranchProject && scm instanceof GitSCM) { // Case 3.1 - Multi branch workflow job
                GitSCM gitScm = (GitSCM) scm;
                MultiBranchProject<?, ?> multiBranchProject = (MultiBranchProject<?, ?>) parent;
                multiBranchProject
                        .getSources()
                        .stream()
                        .map(BranchSource::getSource)
                        .filter(BitbucketSCMSource.class::isInstance)
                        .map(BitbucketSCMSource.class::cast)
                        .filter(bbsSource ->
                                filterSource(gitScm, bbsSource))
                        .findFirst()
                        .ifPresent(scmSource -> scmSource.getBitbucketSCMRepository().buildEnvironment(envs));
            } else { // Case 3.2 - Part of pipeline run
                // Handle only SCM jobs.
                SCMTriggerItem scmItem = (SCMTriggerItem) job;
                scmItem.getSCMs()
                        .stream()
                        .filter(BitbucketSCM.class::isInstance)
                        .map(BitbucketSCM.class::cast)
                        .filter(bScm -> {
                            GitSCM bGitScm = bScm.getGitSCM();
                            return bGitScm != null &&
                                    Objects.equals(bGitScm.getKey(), scm.getKey());
                        })
                        .findFirst()
                        .ifPresent(bScm -> bScm.getBitbucketSCMRepository().buildEnvironment(envs));
            }
        }
    }

    @VisibleForTesting
    ItemGroup<?> getJobParent(Job<?, ?> job) {
        return job.getParent();
    }

    /**
     * The assumption is the remote URL specified in GitSCM should be same as remote URL specified in
     * Bitbucket Source.
     */
    @VisibleForTesting
    boolean filterSource(GitSCM gitScm, BitbucketSCMSource bbsSource) {
        return gitScm.getUserRemoteConfigs()
                .stream()
                .anyMatch(userRemoteConfig ->
                        Objects.equals(userRemoteConfig.getUrl(), bbsSource.getRemote()));
    }
}
