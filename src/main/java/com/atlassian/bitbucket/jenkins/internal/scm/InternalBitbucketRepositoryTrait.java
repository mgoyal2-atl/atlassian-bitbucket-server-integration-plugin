package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import jenkins.plugins.git.traits.GitSCMExtensionTrait;
import jenkins.plugins.git.traits.GitSCMExtensionTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

/**
 * This class is a trait that is used only by {@link BitbucketSCMSource}. The descriptors are hidden from the UI,
 * and trait lists containing this are filtered before displaying in the UI.
 *
 * @see {@link InternalBitbucketRepositoryExtension}
 */
public class InternalBitbucketRepositoryTrait extends GitSCMExtensionTrait<InternalBitbucketRepositoryExtension> {

    @DataBoundConstructor
    public InternalBitbucketRepositoryTrait(InternalBitbucketRepositoryExtension extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Bitbucket revision information trait";
        }
    }

    @Restricted({DoNotUse.class})
    @Extension
    public static class Hider extends DescriptorVisibilityFilter {

        public boolean filter(@CheckForNull Object context, Descriptor descriptor) {
            return !(descriptor instanceof DescriptorImpl);
        }
    }
}
