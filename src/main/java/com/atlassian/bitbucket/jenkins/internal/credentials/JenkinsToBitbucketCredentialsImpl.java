package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.model.Item;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Base64;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;

@Singleton
public class JenkinsToBitbucketCredentialsImpl implements JenkinsToBitbucketCredentials {

    @Override
    public BitbucketCredentials toBitbucketCredentials(@Nullable String credentialId) {
        return toBitbucketCredentials(credentialId, null);
    }

    @Override
    public BitbucketCredentials toBitbucketCredentials(@Nullable String credentialId, @Nullable Item context) {
        return CredentialUtils.getCredentials(credentialId, context)
                .map(this::toBitbucketCredentials).orElse(ANONYMOUS_CREDENTIALS);
    }

    @Override
    public BitbucketCredentials toBitbucketCredentials(@Nullable Credentials credentials) {
        if (credentials instanceof StringCredentials) {
            String bearerToken = ((StringCredentials) credentials).getSecret().getPlainText();
            return getBearerCredentials(bearerToken);
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            return getBasicCredentials(upc.getUsername(), upc.getPassword().getPlainText());
        } else if (credentials instanceof BitbucketTokenCredentials) {
            String bearerToken = ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
            return getBearerCredentials(bearerToken);
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }

    public static BitbucketCredentials getBearerCredentials(String bearerToken) {
        return () -> "Bearer " + bearerToken;
    }

    private static BitbucketCredentials getBasicCredentials(String username, String password) {
        String authorization = username + ':' + password;
        return () -> "Basic " + Base64.getEncoder().encodeToString(authorization.getBytes(Charsets.UTF_8));
    }
}
