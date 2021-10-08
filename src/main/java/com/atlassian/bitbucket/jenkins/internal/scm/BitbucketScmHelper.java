package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NoContentException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRef;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;

import javax.annotation.Nullable;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketScmHelper {

    private static final Logger LOGGER = Logger.getLogger(BitbucketScmHelper.class.getName());
    private final BitbucketClientFactory clientFactory;

    public BitbucketScmHelper(String bitbucketBaseUrl,
                              BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                              BitbucketCredentials credentials) {
        clientFactory = bitbucketClientFactoryProvider.getClient(bitbucketBaseUrl, credentials);
    }

    @Nullable
    public BitbucketRef getDefaultBranch(String projectKey, String repositorySlug) {
        try {
            return clientFactory.getProjectClient(projectKey)
                    .getRepositoryClient(repositorySlug)
                    .getDefaultBranch();
        } catch (NotFoundException nfe) {
            LOGGER.info("Error getting the default branch: Cannot find the repository " + projectKey + "/" + repositorySlug);
        } catch (NoContentException nce) {
            LOGGER.info("Error getting the default branch: Repository " + projectKey + "/" + repositorySlug + " is empty");
        } catch (BitbucketClientException bce) {
            // Something went wrong with the request to Bitbucket
            LOGGER.info(
                    "Error getting the default branch: Something went wrong when trying to contact Bitbucket Server: " +
                    bce.getMessage());
        }
        return null;
    }

    public BitbucketRepository getRepository(String projectName, String repositoryName) {
        if (isBlank(projectName) || isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The projectName and repositoryName must not be blank");
            return new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName),
                    repositoryName, RepositoryState.AVAILABLE);
        }
        try {
            BitbucketProject project = getProjectByNameOrKey(projectName, clientFactory);
            try {
                return getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
            } catch (NotFoundException e) {
                LOGGER.info("Error creating the Bitbucket SCM: Cannot find the repository " + project.getName() + "/" +
                            repositoryName);
                return new BitbucketRepository(-1, repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
            } catch (BitbucketClientException e) {
                // Something went wrong with the request to Bitbucket
                LOGGER.info(
                        "Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " +
                        e.getMessage());
                return new BitbucketRepository(-1, repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
            }
        } catch (NotFoundException e) {
            LOGGER.info("Error creating the Bitbucket SCM: Cannot find the project " + projectName);
            return new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName), repositoryName, RepositoryState.AVAILABLE);
        } catch (BitbucketClientException e) {
            // Something went wrong with the request to Bitbucket
            LOGGER.info(
                    "Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " +
                    e.getMessage());
            return new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName), repositoryName, RepositoryState.AVAILABLE);
        }
    }
}
