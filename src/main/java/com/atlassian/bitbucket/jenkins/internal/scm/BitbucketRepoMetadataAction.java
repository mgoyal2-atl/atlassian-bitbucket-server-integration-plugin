package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.InvisibleAction;

public class BitbucketRepoMetadataAction extends InvisibleAction {

    private final BitbucketSCMRepository repository;

    public BitbucketRepoMetadataAction(BitbucketSCMRepository repository) {
        this.repository = repository;
    }

    public BitbucketSCMRepository getRepository() {
        return repository;
    }
}
