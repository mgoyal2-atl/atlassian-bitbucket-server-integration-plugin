package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.io.IOException;

@Extension
public class BitbucketSCMEnvironmentContributor extends EnvironmentContributor {

    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        BitbucketSCMRevisionAction revisionAction = run.getAction(BitbucketSCMRevisionAction.class);
        if (revisionAction == null) {
            // No environment to contribute yet
            return;
        }
        revisionAction.getBitbucketSCMRepo().buildEnvironment(envs);
    }
}
