package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.CheckForNull;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BitbucketSCMEnvironmentContributorTest {

    private static final String REMOTE_URL = "http://localhost:7990/bitbucket/scm/project_1/rep_1.git";

    @Mock
    BitbucketSCMRepository repository;

    @Spy
    BitbucketSCMEnvironmentContributor contributor;

    @Test
    public void testBuildEnvironmentForAbstractProjectWithoutBitbucketSCM() throws Exception {
        FreeStyleProject job = mock(FreeStyleProject.class);
        GitSCM scm = mockGitSCM();
        when(job.getScm()).thenReturn(scm);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForAbstractProjectWithBitbucketSCM() throws Exception {
        FreeStyleProject job = mock(FreeStyleProject.class);
        BitbucketSCM scm = mockBitbucketSCM();
        when(job.getScm()).thenReturn(scm);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithoutSCM() throws Exception {
        WorkflowJob job = mockWorkflowJob(null);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithDifferentSCM() throws Exception {
        SCM scm = mock(SCM.class);
        WorkflowJob job = mockWorkflowJob(scm);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithBitbucketSCM() throws Exception {
        BitbucketSCM bitbucketSCM = mockBitbucketSCM();
        WorkflowJob job = mockWorkflowJob(bitbucketSCM);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithMultibranchParent() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        BitbucketSCMSource bitbucketSCMSource = getBitbucketSCMSource();
        MultiBranchProject parent = mock(MultiBranchProject.class);
        when(parent.getSources()).thenReturn(singletonList(new BranchSource(bitbucketSCMSource)));
        doReturn(parent).when(contributor).getJobParent(job);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithMultibranchParentButNotBitbucketSCMSource() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        MultiBranchProject parent = mock(MultiBranchProject.class);
        SCMSource notBitbucketScmSource = mock(SCMSource.class);
        when(parent.getSources()).thenReturn(singletonList(new BranchSource(notBitbucketScmSource)));
        doReturn(parent).when(contributor).getJobParent(job);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForWorkflowJobWithMultibranchParentButRemotesDontMatch() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        BitbucketSCMSource bitbucketSCMSource = getBitbucketSCMSource();
        when(bitbucketSCMSource.getRemote()).thenReturn("something else");
        MultiBranchProject parent = mock(MultiBranchProject.class);
        when(parent.getSources()).thenReturn(singletonList(new BranchSource(bitbucketSCMSource)));
        doReturn(parent).when(contributor).getJobParent(job);
        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForPipelineJobWithNotBitbucketSCM() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        doReturn(null).when(contributor).getJobParent(job);

        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForPipelineJobWithDifferentSCM() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        BitbucketSCM bitbucketSCM = mockBitbucketSCM(); // Will generate a different git scm
        doReturn(singletonList(bitbucketSCM)).when(job).getSCMs();
        doReturn(null).when(contributor).getJobParent(job);

        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository, never()).buildEnvironment(envs);
    }

    @Test
    public void testBuildEnvironmentForPipelineJob() throws Exception {
        GitSCM gitSCM = mockGitSCM();
        WorkflowJob job = mockWorkflowJob(gitSCM);
        BitbucketSCM bitbucketSCM = mockBitbucketSCM(gitSCM);
        doReturn(singletonList(bitbucketSCM)).when(job).getSCMs();
        doReturn(null).when(contributor).getJobParent(job);

        EnvVars envs = new EnvVars();

        contributor.buildEnvironmentFor(job, envs, null);

        verify(repository).buildEnvironment(envs);
    }

    private BitbucketSCMSource getBitbucketSCMSource() {
        BitbucketSCMSource bitbucketSCMSource = mock(BitbucketSCMSource.class);
        when(bitbucketSCMSource.getRemote()).thenReturn(REMOTE_URL);
        when(bitbucketSCMSource.getBitbucketSCMRepository()).thenReturn(repository);
        return bitbucketSCMSource;
    }

    private WorkflowJob mockWorkflowJob(@CheckForNull SCM scm) {
        WorkflowJob job = mock(WorkflowJob.class);
        when(job.getTypicalSCM()).thenReturn(scm);
        doReturn(singletonList(scm)).when(job).getSCMs();
        return job;
    }

    private GitSCM mockGitSCM() {
        return mockGitSCM(UUID.randomUUID().toString());
    }

    private GitSCM mockGitSCM(String gitScmKey) {
        GitSCM gitSCM = mock(GitSCM.class);
        UserRemoteConfig gitRemoteConfig = new UserRemoteConfig(REMOTE_URL, null, null, null);
        when(gitSCM.getUserRemoteConfigs()).thenReturn(singletonList(gitRemoteConfig));
        when(gitSCM.getKey()).thenReturn(gitScmKey);
        return gitSCM;
    }

    private BitbucketSCM mockBitbucketSCM() {
        GitSCM gitSCM = mockGitSCM();
        return mockBitbucketSCM(gitSCM);
    }

    private BitbucketSCM mockBitbucketSCM(GitSCM gitSCM) {
        BitbucketSCM bitbucketSCM = mock(BitbucketSCM.class);
        when(bitbucketSCM.getBitbucketSCMRepository()).thenReturn(repository);
        when(bitbucketSCM.getGitSCM()).thenReturn(gitSCM);
        return bitbucketSCM;
    }
}