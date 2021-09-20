package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.EnvVars;
import hudson.model.Run;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BitbucketSCMEnvironmentContributorTest {

    @Spy
    BitbucketSCMEnvironmentContributor contributor;

    @Test
    public void testBuildEnvironment() throws Exception {
        Run run = mock(Run.class);
        BitbucketSCMRevisionAction action = mock(BitbucketSCMRevisionAction.class);
        when(run.getAction(BitbucketSCMRevisionAction.class)).thenReturn(action);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(action.getBitbucketSCMRepo()).thenReturn(repo);
        EnvVars env = new EnvVars();

        contributor.buildEnvironmentFor(run, env, null);

        verify(repo).buildEnvironment(env);
    }
}