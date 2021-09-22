package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
        // Get the underlying Git SCM
        GitSCM gitSCM = null;
        if (scm instanceof BitbucketSCM) {
            gitSCM = ((BitbucketSCM) scm).getGitSCM();
        } else if (scm instanceof GitSCM) {
            gitSCM = (GitSCM) scm;
        }
        // If the SCM is not a Bitbucket or Git one then it isn't using a Bitbucket repository
        // If the BitbucketSCM doesn't have an underlying gitSCM then we can't get the branch & commit info
        if (gitSCM == null) {
            return;
        }

        // Get the Bitbucket repository off the environment
        BitbucketSCMRepository repo;
        try {
            EnvVars environment = build.getEnvironment(listener);
            repo = BitbucketSCMRepository.fromEnvironment(environment);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Error reading the environment in LocalSCMListener: Bitbucket Server build statuses will not be sent");
            return;
        }
        // If there's no repo in the environment, then we don't know where to send the build status to
        if (repo == null) {
            return;
        }

        Map<String, String> env = getGitEnvironment(build, gitSCM);
        String branch = env.get(GitSCM.GIT_BRANCH);
        String refName = branch != null ? gitSCM.deriveLocalBranchName(branch) : null;
        String revisionSha1 = env.get(GitSCM.GIT_COMMIT);

        BitbucketRevisionAction revisionAction = new BitbucketRevisionAction(repo, refName, revisionSha1);
        build.addAction(revisionAction);

        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }

    private Map<String, String> getGitEnvironment(Run<?, ?> build, GitSCM gitSCM) {
        Map<String, String> env = new HashMap<>();
        gitSCM.buildEnvironment(build, env);
        return env;
    }
}
