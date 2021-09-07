package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Extension
public class LocalSCMListener extends SCMListener {

    @Inject
    private BuildStatusPoster buildStatusPoster;

    public LocalSCMListener() {
    }

    LocalSCMListener(BuildStatusPoster buildStatusPoster) {
        this.buildStatusPoster = buildStatusPoster;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {
        BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);
        if (revisionAction == null) {
            // Not a Bitbucket SCM
            return;
        }
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
        // TODO: Remove old methods if this works
    }

    @VisibleForTesting
    ItemGroup<?> getJobParent(Job<?, ?> job) {
        return job.getParent();
    }

    @VisibleForTesting
    boolean isWorkflowRun(Run<?, ?> build) {
        return build instanceof WorkflowRun;
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

    private void handleBitbucketSCMCheckout(Run<?, ?> build, BitbucketSCM scm, TaskListener listener) {
        if (scm.getServerId() != null) {
            GitSCM gitSCM = scm.getGitSCM();
            if (gitSCM != null) {
                handleCheckout(scm, gitSCM, build, listener);
            }
        }
    }

    private void handleCheckout(BitbucketSCM bitbucketScm,
                                GitSCM underlyingScm,
                                Run<?, ?> build,
                                TaskListener listener) {
        handleCheckout(bitbucketScm.getBitbucketSCMRepository(), underlyingScm, build, listener);
    }

    private void handleCheckout(BitbucketSCMRepository bitbucketSCMRepository,
                                GitSCM underlyingScm,
                                Run<?, ?> build,
                                TaskListener listener) {
        Map<String, String> env = new HashMap<>();
        underlyingScm.buildEnvironment(build, env);

        String branch = env.get(GitSCM.GIT_BRANCH);
        String refName = branch != null ? underlyingScm.deriveLocalBranchName(branch) : null;
        BitbucketRevisionAction revisionAction =
                new BitbucketRevisionAction(bitbucketSCMRepository, refName, env.get(GitSCM.GIT_COMMIT));
        build.addAction(revisionAction);
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }
}
