package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketProjectClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefType;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketScmHelperTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());
    private BitbucketScmHelper bitbucketScmHelper;
    @Mock
    private BitbucketClientFactory clientFactory;
    @Mock
    private BitbucketSearchClient searchClient;
    @Mock
    private BitbucketProjectClient projectClient;
    @Mock
    private BitbucketRepositoryClient repositoryClient;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    @Before
    public void setup() {
        when(clientFactory.getSearchClient(any())).thenReturn(searchClient);
        when(clientFactory.getProjectClient(any())).thenReturn(projectClient);
        when(clientFactory.getProjectClient(any()).getRepositoryClient(any())).thenReturn(repositoryClient);
        when(searchClient.findProjects()).thenReturn(new BitbucketPage<>());
        when(searchClient.findRepositories(any())).thenReturn(new BitbucketPage<>());
        // Clear the latestProject & latestRepositories cache
        BitbucketSearchHelper.findRepositories("", "", clientFactory);
        BitbucketSearchHelper.findProjects("", clientFactory);
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = mock(BitbucketClientFactoryProvider.class);
        when(bitbucketClientFactoryProvider.getClient(eq("myBaseUrl"), any(BitbucketCredentials.class)))
                .thenReturn(clientFactory);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(nullable(String.class)))
                .thenReturn(mock(BitbucketCredentials.class));
        bitbucketScmHelper =
                new BitbucketScmHelper("myBaseUrl",
                        bitbucketClientFactoryProvider,
                        BitbucketCredentials.ANONYMOUS_CREDENTIALS);
    }

    @Test
    public void testGetRepository() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        BitbucketPage<BitbucketRepository> repositoryPage = new BitbucketPage<>();
        BitbucketRepository expectedRepo =
                new BitbucketRepository(0, "my repo", null, expectedProject, "myRepo", RepositoryState.AVAILABLE);
        repositoryPage.setValues(singletonList(expectedRepo));
        when(searchClient.findRepositories("my repo")).thenReturn(repositoryPage);

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("myRepo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenProjectBitbucketClientException() {
        when(searchClient.findProjects()).thenThrow(new BitbucketClientException("some error", 500, "an error"));
        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("my project"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenProjectNameIsBlank() {
        BitbucketRepository repo = bitbucketScmHelper.getRepository("", "repo");
        assertThat(repo.getName(), equalTo("repo"));
        assertThat(repo.getSlug(), equalTo("repo"));
        assertThat(repo.getProject().getKey(), equalTo(""));
        assertThat(repo.getProject().getName(), equalTo(""));
    }

    @Test
    public void testGetRepositoryWhenProjectNotFound() {
        when(searchClient.findProjects()).thenThrow(new NotFoundException("my message", "my body"));
        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("my project"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryBitbucketClientException() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new BitbucketClientException("", 500, ""));

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryNameIsBlank() {
        BitbucketRepository repo = bitbucketScmHelper.getRepository("project", "");
        assertThat(repo.getName(), equalTo(""));
        assertThat(repo.getSlug(), equalTo(""));
        assertThat(repo.getProject().getKey(), equalTo("project"));
        assertThat(repo.getProject().getName(), equalTo("project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryNotFound() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new NotFoundException("", ""));

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }
    
    @Test
    public void testGetDefaultBranch() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        BitbucketPage<BitbucketRepository> repositoryPage = new BitbucketPage<>();
        BitbucketRepository expectedRepo =
                new BitbucketRepository(0, "my repo", null, expectedProject, "myRepo", RepositoryState.AVAILABLE);
        repositoryPage.setValues(singletonList(expectedRepo));
        when(searchClient.findRepositories("my repo")).thenReturn(repositoryPage);
        BitbucketDefaultBranch expectedBranch =
                new BitbucketDefaultBranch("ref/head/master", "master", BitbucketRefType.BRANCH, "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", "1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0", true);
        when(repositoryClient.getDefaultBranch()).thenReturn(expectedBranch);

        BitbucketDefaultBranch branch = bitbucketScmHelper.getDefaultBranch("my project", "my repo").get();
        assertThat(branch.getId(), equalTo("ref/head/master"));
        assertThat(branch.getDisplayId(), equalTo("master"));
        assertThat(branch.getType(), equalTo(BitbucketRefType.BRANCH));
        assertThat(branch.getLatestCommit(), equalTo("1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0"));
        assertThat(branch.getLatestChangeset(), equalTo("1c4c3f92b4f8078e04b7f5a64ce7476a2d4276e0"));
        assertThat(branch.isDefault(), equalTo(true));
    }
    
    @Test
    public void testGetDefaultBranchWhenProjectBitbucketClientException() {
        when(searchClient.findProjects()).thenThrow(new BitbucketClientException("some error", 500, "an error"));
        
        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("my project", "my repo");
        assertThat(branch, equalTo(Optional.empty()));
    }

    @Test
    public void testGetDefaultBranchWhenProjectNameIsBlank() {
        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("", "repo");
        assertThat(branch, equalTo(Optional.empty()));
    }

    @Test
    public void testGetDefaultBranchWhenProjectNotFound() {
        when(searchClient.findProjects()).thenThrow(new NotFoundException("my message", "my body"));

        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("my project", "my repo");
        assertThat(branch, equalTo(Optional.empty()));
    }

    @Test
    public void testGetDefaultBranchWhenRepositoryBitbucketClientException() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new BitbucketClientException("", 500, ""));

        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("my project", "my repo");
        assertThat(branch, equalTo(Optional.empty()));
    }

    @Test
    public void testGetDefaultBranchWhenRepositoryNameIsBlank() {
        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("project", "");
        assertThat(branch, equalTo(Optional.empty()));
    }

    @Test
    public void testGetDefaultBranchWhenRepositoryNotFound() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new NotFoundException("", ""));

        Optional<BitbucketDefaultBranch> branch = bitbucketScmHelper.getDefaultBranch("my project", "my repo");
        assertThat(branch, equalTo(Optional.empty()));
    }
}
