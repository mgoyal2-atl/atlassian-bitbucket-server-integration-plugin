package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.EnvVars;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketSCMRepository {

    public static final String BBS_CREDENTIALS_ID = "BBS_CREDENTIALS_ID";
    public static final String BBS_SSH_CREDENTIALS_ID = "BBS_SSH_CREDENTIALS_ID";
    public static final String BBS_PROJECT_KEY = "BBS_PROJECT_KEY";
    public static final String BBS_PROJECT_NAME = "BBS_PROJECT_NAME";
    public static final String BBS_REPOSITORY_NAME = "BBS_REPOSITORY_NAME";
    public static final String BBS_REPOSITORY_SLUG = "BBS_REPOSITORY_SLUG";
    public static final String BBS_SERVER_ID = "BBS_SERVER_ID";
    public static final String BBS_MIRROR_NAME = "BBS_MIRROR_NAME";

    private final String credentialsId;
    private final String sshCredentialsId;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final String mirrorName;

    public BitbucketSCMRepository(@Nullable String credentialsId, @CheckForNull String sshCredentialsId,
                                  String projectName, String projectKey, String repositoryName, String repositorySlug,
                                  @CheckForNull String serverId, String mirrorName) {
        this.credentialsId = credentialsId;
        this.sshCredentialsId = sshCredentialsId;
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.repositoryName = repositoryName;
        this.repositorySlug = repositorySlug;
        this.serverId = serverId;
        this.mirrorName = mirrorName;
    }

    @CheckForNull
    public static BitbucketSCMRepository fromEnvironment(EnvVars environment) {
        String projectKey = environment.get(BBS_PROJECT_KEY);
        String projectName = environment.get(BBS_PROJECT_NAME);
        String repositoryName = environment.get(BBS_REPOSITORY_NAME);
        String repositorySlug = environment.get(BBS_REPOSITORY_SLUG);
        String serverId = environment.get(BBS_SERVER_ID);
        String mirrorName = environment.get(BBS_MIRROR_NAME);
        if (projectKey == null || projectName == null || repositoryName == null || repositorySlug == null ||
                serverId == null || mirrorName == null) {
            // The environment does not have all of the required fields
            return null;
        }
        return new BitbucketSCMRepository(environment.get(BBS_CREDENTIALS_ID), environment.get(BBS_SSH_CREDENTIALS_ID),
                projectName, projectKey, repositoryName, repositorySlug, serverId, mirrorName);
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public String getSshCredentialsId() {
        return sshCredentialsId;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    @CheckForNull
    public String getServerId() {
        return serverId;
    }

    public String getMirrorName() {
        return mirrorName;
    }

    public boolean isMirrorConfigured() {
        return !isEmpty(mirrorName);
    }

    public boolean isPersonal() {
        return projectKey.startsWith("~");
    }

    public void buildEnvironment(EnvVars environment) {
        environment.putIfNotNull(BBS_CREDENTIALS_ID, credentialsId);
        environment.putIfNotNull(BBS_SSH_CREDENTIALS_ID, sshCredentialsId);
        environment.put(BBS_PROJECT_KEY, projectKey);
        environment.put(BBS_PROJECT_NAME, projectName);
        environment.put(BBS_REPOSITORY_NAME, repositoryName);
        environment.put(BBS_REPOSITORY_SLUG, repositorySlug);
        environment.put(BBS_SERVER_ID, serverId);
        environment.put(BBS_MIRROR_NAME, mirrorName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketSCMRepository that = (BitbucketSCMRepository) o;
        return Objects.equals(credentialsId, that.credentialsId) && Objects.equals(sshCredentialsId, that.sshCredentialsId) && Objects.equals(projectKey, that.projectKey) && Objects.equals(projectName, that.projectName) && Objects.equals(repositoryName, that.repositoryName) && Objects.equals(repositorySlug, that.repositorySlug) && Objects.equals(serverId, that.serverId) && Objects.equals(mirrorName, that.mirrorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialsId, sshCredentialsId, projectKey, projectName, repositoryName, repositorySlug, serverId, mirrorName);
    }
}
