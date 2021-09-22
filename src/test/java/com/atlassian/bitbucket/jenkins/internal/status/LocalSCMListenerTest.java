package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository.*;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Silent.class)
public class LocalSCMListenerTest {

    private final EnvVars ENV_VARS = new EnvVars();
    private final Map<String, String> BUILD_MAP = new HashMap<>();

    @Mock
    private GitSCM gitSCM;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Mock
    private AbstractBuild run;
    @Mock
    private TaskListener taskListener;
    @Mock
    private BitbucketSCM bitbucketSCM;
    private BitbucketSCMRepository scmRepository;
    private LocalSCMListener listener;

    @Before
    public void setup() throws Exception {
        BUILD_MAP.put(GitSCM.GIT_BRANCH, "master");
        BUILD_MAP.put(GitSCM.GIT_COMMIT, "c1");
        when(bitbucketSCM.getGitSCM()).thenReturn(gitSCM);
        doAnswer(invocation -> {
            Map<String, String> m = (Map<String, String>) invocation.getArguments()[1];
            m.putAll(BUILD_MAP);
            return null;
        }).when(gitSCM).buildEnvironment(notNull(), anyMap());
        RemoteConfig rc = new RemoteConfig(new Config(), "origin");
        when(gitSCM.getRepositories()).thenReturn(singletonList(rc));
        when(taskListener.getLogger()).thenReturn(System.out);
        listener = spy(new LocalSCMListener(buildStatusPoster));

        // Build the environment
        ENV_VARS.putIfNotNull(BBS_CREDENTIALS_ID, "credentialsId");
        ENV_VARS.putIfNotNull(BBS_SSH_CREDENTIALS_ID, "sshCredentialsId");
        ENV_VARS.put(BBS_PROJECT_KEY, "projectKey");
        ENV_VARS.put(BBS_PROJECT_NAME, "projectName");
        ENV_VARS.put(BBS_REPOSITORY_NAME, "repositoryName");
        ENV_VARS.put(BBS_REPOSITORY_SLUG, "repositorySlug");
        ENV_VARS.put(BBS_SERVER_ID, "serverId");
        ENV_VARS.put(BBS_MIRROR_NAME, "mirrorName");
        when(run.getEnvironment(taskListener)).thenReturn(ENV_VARS);
        scmRepository = BitbucketSCMRepository.fromEnvironment(ENV_VARS);
    }

    @Test
    public void testOnCheckoutWithNonGitSCMDoesNotPostBuildStatus() throws Exception {
        SCM scm = mock(SCM.class);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutErrorGettingEnvironment() throws Exception {
        doThrow(new IOException()).when(run).getEnvironment(taskListener);

        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutNoRepositoryInEnvironment() throws Exception {
        when(run.getEnvironment(taskListener)).thenReturn(new EnvVars());

        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutBitbucketSCM() throws Exception {
        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(run), eq(taskListener));
    }

    @Test
    public void testOnCheckoutGitSCM() throws Exception {
        listener.onCheckout(run, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(run), eq(taskListener));
    }

    @Test
    public void testOnCheckoutBitbucketSCMHasNoGitSCM() throws Exception {
        when(bitbucketSCM.getGitSCM()).thenReturn(null);

        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

}
