package com.atlassian.bitbucket.jenkins.internal.scm;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketSCMRepository {

    private final String credentialsId;
    private final String sshCredentialsId;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final String serverId;
    private final String mirrorName;

    public BitbucketSCMRepository(@Nullable String credentialsId, @Nullable String sshCredentialsId, String projectName, String projectKey,
                                  String repositoryName, String repositorySlug, @Nullable String serverId,
                                  String mirrorName) {
        this.credentialsId = credentialsId;
        this.sshCredentialsId = sshCredentialsId;
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.repositoryName = repositoryName;
        this.repositorySlug = repositorySlug;
        this.serverId = serverId;
        this.mirrorName = mirrorName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialsId, sshCredentialsId, projectKey, projectName, repositoryName, repositorySlug, serverId, mirrorName);
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
        return StringUtils.equals(credentialsId, that.credentialsId) &&
               StringUtils.equals(sshCredentialsId, that.sshCredentialsId) &&
               StringUtils.equals(projectKey, that.projectKey) &&
               StringUtils.equals(projectName, that.projectName) &&
               StringUtils.equals(repositoryName, that.repositoryName) &&
               StringUtils.equals(repositorySlug, that.repositorySlug) &&
               StringUtils.equals(serverId, that.serverId) &&
               StringUtils.equals(mirrorName, that.mirrorName);
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    @Nullable
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
}
