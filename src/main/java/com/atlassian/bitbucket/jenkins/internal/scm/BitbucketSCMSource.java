package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRef;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.AbstractWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.Stash;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.model.RepositoryState.AVAILABLE;
import static java.lang.Math.max;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BitbucketSCMSource extends SCMSource {

    private static final Duration BRANCH_LOCK_CACHE_DURATION = Duration.ofHours(6L);
    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
    private final List<SCMSourceTrait> traits;
    private BitbucketRef defaultBranch;
    private transient ReadWriteLock defaultBranchLock = new ReentrantReadWriteLock();
    private CustomGitSCMSource gitSCMSource;
    private transient Instant nextTimeToCheckBranchLock;
    private BitbucketSCMRepository repository;
    private volatile boolean webhookRegistered;

    @DataBoundConstructor
    public BitbucketSCMSource(
            @CheckForNull String id,
            @CheckForNull String credentialsId,
            @CheckForNull String sshCredentialsId,
            @CheckForNull List<SCMSourceTrait> traits,
            @CheckForNull String projectName,
            @CheckForNull String repositoryName,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName) {
        super.setId(id);
        nextTimeToCheckBranchLock = Instant.now();
        this.traits = new ArrayList<>();
        if (traits != null) {
            this.traits.addAll(traits);
        }

        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(serverId);
        if (!mayBeServerConf.isPresent()) {
            LOGGER.info("No Bitbucket Server configuration for serverId " + serverId);
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }

        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        GlobalCredentialsProvider globalCredentialsProvider = serverConfiguration.getGlobalCredentialsProvider(
                format("Bitbucket SCM: Query Bitbucket for project [%s] repo [%s] mirror[%s]",
                        projectName,
                        repositoryName,
                        mirrorName));
        BitbucketScmHelper scmHelper =
                descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(),
                        globalCredentialsProvider.getGlobalAdminCredentials().orElse(null));
        if (isBlank(projectName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }
        if (isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }
        String selfLink = "";
        if (isNotBlank(mirrorName)) {
            try {
                EnrichedBitbucketMirroredRepository mirroredRepository =
                        descriptor.createMirrorHandler(scmHelper)
                                .fetchRepository(
                                        new MirrorFetchRequest(
                                                serverConfiguration.getBaseUrl(),
                                                credentialsId,
                                                globalCredentialsProvider,
                                                projectName,
                                                repositoryName,
                                                mirrorName));
                setRepositoryDetails(credentialsId, sshCredentialsId, serverId, mirroredRepository);
                selfLink = mirroredRepository.getRepository().getSelfLink();
            } catch (MirrorFetchException ex) {
                setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            }
        } else {
            BitbucketRepository localRepo = scmHelper.getRepository(projectName, repositoryName);
            setRepositoryDetails(credentialsId, sshCredentialsId, serverId, "", localRepo);
            selfLink = localRepo.getSelfLink();
        }
        //self link contains `/browse` which we must trim off.
        String repositoryUrl = selfLink.substring(0, max(selfLink.lastIndexOf("/browse"), 0));
        lookupDefaultBranch();
        gitSCMSource.setBrowser(new Stash(repositoryUrl));
        gitSCMSource.setId(getId() + "-git-scm");
    }

    /**
     * Regenerate SCM by looking up new repo URLs etc.
     *
     * @param oldScm old scm to copy values from
     */
    public BitbucketSCMSource(BitbucketSCMSource oldScm) {
        this(oldScm.getId(), oldScm.getCredentialsId(), oldScm.getSshCredentialsId(), oldScm.getTraits(),
                oldScm.getProjectName(), oldScm.getRepositoryName(), oldScm.getServerId(), oldScm.getMirrorName());
    }

    @Override
    public void afterSave() {
        super.afterSave();
        // The git scm needs an owner set to resolve non-global credentials
        getGitSCMSource().setOwner(getOwner());

        if (!webhookRegistered && isValid()) {
            SCMSourceOwner owner = getOwner();
            if (owner instanceof ComputedFolder) {
                ComputedFolder project = (ComputedFolder) owner;
                DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
                BitbucketServerConfiguration bitbucketServerConfiguration = descriptor.getConfiguration(getServerId())
                        .orElseThrow(() -> new BitbucketClientException(
                                "Server config not found for input server id " + getServerId()));
                List<BitbucketWebhookMultibranchTrigger> triggers = getTriggers(project);
                boolean isPullRequestTrigger =
                        triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isPullRequestTrigger);
                boolean isRefTrigger = triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isRefTrigger);

                try {
                    descriptor.getRetryingWebhookHandler().register(
                            bitbucketServerConfiguration.getBaseUrl(),
                            bitbucketServerConfiguration.getGlobalCredentialsProvider(owner),
                            repository, isPullRequestTrigger, isRefTrigger);
                } catch (WebhookRegistrationFailed webhookRegistrationFailed) {
                    LOGGER.severe("Webhook failed to register- token credentials assigned to " +
                                  bitbucketServerConfiguration.getServerName()
                                  +
                                  " do not have admin access. Please reconfigure your instance in the Manage Jenkins -> Settings page.");
                }
            }
        }
    }

    @Override
    public SCM build(SCMHead head, @CheckForNull SCMRevision revision) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Building SCM for " + head.getName() + " at revision " + revision);
        }
        return getGitSCMSource().build(head, revision);
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        BitbucketSCMRepository repository = getBitbucketSCMRepository();
        return repository.isPersonal() ? repository.getProjectKey() : repository.getProjectName();
    }

    public String getRemote() {
        return getGitSCMSource().getRemote();
    }

    public String getRepositoryName() {
        return getBitbucketSCMRepository().getRepositoryName();
    }

    public String getRepositorySlug() {
        return getBitbucketSCMRepository().getRepositorySlug();
    }

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    @CheckForNull
    public String getSshCredentialsId() {
        return getBitbucketSCMRepository().getSshCredentialsId();
    }

    @Override
    public List<SCMSourceTrait> getTraits() {
        return traits;
    }

    public boolean isValid() {
        return getMirrorName() != null && isNotBlank(getProjectKey()) && isNotBlank(getProjectName()) &&
               isNotBlank(getRemote()) && isNotBlank(getRepositoryName()) && isNotBlank(getRepositorySlug()) &&
               isNotBlank(getServerId());
    }

    public boolean isWebhookRegistered() {
        return webhookRegistered;
    }

    public void setWebhookRegistered(boolean webhookRegistered) {
        this.webhookRegistered = webhookRegistered;
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException, InterruptedException {
        if (getOwner() instanceof ComputedFolder && event != null) {
            ComputedFolder<?> owner = (ComputedFolder<?>) getOwner();
            Object payload = event.getPayload();
            if (payload instanceof AbstractWebhookEvent) {
                AbstractWebhookEvent webhookEvent = (AbstractWebhookEvent) payload;
                boolean eventApplicable = owner.getTriggers().values().stream()
                        .filter(trg -> trg instanceof BitbucketWebhookMultibranchTrigger)
                        .anyMatch(trig -> (
                                (BitbucketWebhookMultibranchTrigger) trig).isApplicableForEventType(webhookEvent)
                        );
                if (eventApplicable) {
                    getGitSCMSource().accessibleRetrieve(criteria, observer, event, listener);
                }
                return;
            }
        }
        getGitSCMSource().accessibleRetrieve(criteria, observer, event, listener);
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(SCMSourceEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        return Collections.singletonList(new BitbucketRepoMetadataAction(repository));
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head, SCMHeadEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        List<Action> actionList = new ArrayList<>();
        if (head instanceof GitBranchSCMHead && getDefaultBranchName().equals(((GitBranchSCMHead) head).getRef())) {
            SCMSourceOwner owner = getOwner();
            if (owner instanceof Actionable) {
                ((Actionable) owner).getActions(BitbucketRepoMetadataAction.class).stream()
                        .filter(action -> action.getRepository().equals(repository))
                        .findAny()
                        .ifPresent(action -> actionList.add(new PrimaryInstanceMetadataAction()));
            }
        }
        return Collections.unmodifiableList(actionList);
    }

    CustomGitSCMSource getGitSCMSource() {
        return gitSCMSource;
    }

    private String getCloneUrl(List<BitbucketNamedLink> cloneUrls, CloneProtocol cloneProtocol) {
        return cloneUrls.stream()
                .filter(link -> Objects.equals(cloneProtocol.name, link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
    }

    private String getDefaultBranchName() {
        defaultBranchLock.readLock().lock();
        try {
            if (defaultBranch != null) {
                return defaultBranch.getId();
            }
        } finally {
            defaultBranchLock.readLock().unlock();
        }

        return lookupDefaultBranch();
    }

    private List<BitbucketWebhookMultibranchTrigger> getTriggers(ComputedFolder<?> owner) {
        return owner.getTriggers().values().stream()
                .filter(BitbucketWebhookMultibranchTrigger.class::isInstance)
                .map(BitbucketWebhookMultibranchTrigger.class::cast)
                .collect(Collectors.toList());
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private void initialize(String cloneUrl, BitbucketSCMRepository bitbucketSCMRepository) {
        repository = bitbucketSCMRepository;
        String credentialsId = isBlank(bitbucketSCMRepository.getSshCredentialsId()) ?
                bitbucketSCMRepository.getCredentialsId() : bitbucketSCMRepository.getSshCredentialsId();
        UserRemoteConfig remoteConfig =
                new UserRemoteConfig(cloneUrl, bitbucketSCMRepository.getRepositorySlug(), null, credentialsId);
        gitSCMSource = new CustomGitSCMSource(remoteConfig.getUrl(), repository);
        getGitSCMSource().setTraits(traits);
        getGitSCMSource().setCredentialsId(credentialsId);
    }

    private String lookupDefaultBranch() {
        defaultBranchLock.writeLock().lock();
        try {
            if (Instant.now().isAfter(nextTimeToCheckBranchLock) && defaultBranch == null) {
                BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();
                Optional<BitbucketServerConfiguration> mayBeServerConf =
                        descriptor.getConfiguration(repository.getServerId());

                BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
                GlobalCredentialsProvider globalCredentialsProvider = serverConfiguration.getGlobalCredentialsProvider(
                        format("Bitbucket SCM: Query Bitbucket for project [%s] repo [%s] mirror[%s]",
                                repository.getProjectName(),
                                repository.getRepositoryName(),
                                repository.getMirrorName()));
                BitbucketScmHelper scmHelper =
                        descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(),
                                globalCredentialsProvider.getGlobalAdminCredentials().orElse(null));
                defaultBranch = scmHelper.getDefaultBranch(repository.getProjectKey(), repository.getRepositorySlug());
                nextTimeToCheckBranchLock = Instant.now().plus(BRANCH_LOCK_CACHE_DURATION);
            }
        } finally {
            defaultBranchLock.writeLock().unlock();
        }
        return defaultBranch == null ? "" : defaultBranch.getId();
    }

    @SuppressWarnings("Duplicates")
    private void setEmptyRepository(@Nullable String credentialsId,
                                    @Nullable String sshCredentialsId,
                                    @CheckForNull String projectName,
                                    @CheckForNull String repositoryName,
                                    @CheckForNull String serverId,
                                    @CheckForNull String mirrorName) {
        projectName = Objects.toString(projectName, "");
        repositoryName = Objects.toString(repositoryName, "");
        mirrorName = Objects.toString(mirrorName, "");
        BitbucketRepository repository =
                new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName),
                        repositoryName, AVAILABLE);
        setRepositoryDetails(credentialsId, sshCredentialsId, serverId, mirrorName, repository);
    }

    private void setRepositoryDetails(@Nullable String credentialsId, @Nullable String sshCredentialsId,
                                      @Nullable String serverId, String mirrorName, BitbucketRepository repository) {
        CloneProtocol cloneProtocol = isBlank(sshCredentialsId) ? CloneProtocol.HTTP : CloneProtocol.SSH;
        String cloneUrl = getCloneUrl(repository.getCloneUrls(), cloneProtocol);
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getName());
        }
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, sshCredentialsId, repository.getProject().getName(),
                        repository.getProject().getKey(), repository.getName(), repository.getSlug(),
                        serverId, mirrorName);
        initialize(cloneUrl, bitbucketSCMRepository);
    }

    @SuppressWarnings("Duplicates")
    private void setRepositoryDetails(@Nullable String credentialsId, @Nullable String sshCredentialsId,
                                      @Nullable String serverId, EnrichedBitbucketMirroredRepository repository) {
        if (isBlank(serverId)) {
            return;
        }
        CloneProtocol cloneProtocol = isBlank(sshCredentialsId) ? CloneProtocol.HTTP : CloneProtocol.SSH;
        String cloneUrl = getCloneUrl(repository.getMirroringDetails().getCloneUrls(), cloneProtocol);
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getRepository().getName());
        }
        BitbucketRepository underlyingRepo = repository.getRepository();
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, sshCredentialsId, underlyingRepo.getProject().getName(),
                        underlyingRepo.getProject().getKey(), underlyingRepo.getName(), underlyingRepo.getSlug(),
                        serverId, repository.getMirroringDetails().getMirrorName());
        initialize(cloneUrl, bitbucketSCMRepository);
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"unused"})
    public static class DescriptorImpl extends SCMSourceDescriptor implements BitbucketScmFormValidation,
            BitbucketScmFormFill {

        private final GitSCMSource.DescriptorImpl gitScmSourceDescriptor;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        @Inject
        private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;

        public DescriptorImpl() {
            super();
            gitScmSourceDescriptor = new GitSCMSource.DescriptorImpl();
        }

        @Override
        @POST
        public FormValidation doCheckCredentialsId(@AncestorInPath Item context,
                                                   @QueryParameter String credentialsId) {
            return formValidation.doCheckCredentialsId(context, credentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckProjectName(@AncestorInPath Item context,
                                                 @QueryParameter String serverId,
                                                 @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            return formValidation.doCheckProjectName(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public FormValidation doCheckRepositoryName(@AncestorInPath Item context,
                                                    @QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            return formValidation.doCheckRepositoryName(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public FormValidation doCheckServerId(@AncestorInPath Item context,
                                              @QueryParameter String serverId) {
            return formValidation.doCheckServerId(context, serverId);
        }

        @Override
        public FormValidation doCheckSshCredentialsId(@AncestorInPath Item context,
                                                      @QueryParameter String sshCredentialsId) {
            return formValidation.doCheckSshCredentialsId(context, sshCredentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            return formFill.doFillCredentialsIdItems(context, baseUrl, credentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillMirrorNameItems(@AncestorInPath Item context,
                                                  @QueryParameter String serverId,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String projectName,
                                                  @QueryParameter String repositoryName,
                                                  @QueryParameter String mirrorName) {
            return formFill.doFillMirrorNameItems(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        @Override
        @POST
        public HttpResponse doFillProjectNameItems(@AncestorInPath Item context,
                                                   @QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            return formFill.doFillProjectNameItems(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public HttpResponse doFillRepositoryNameItems(@AncestorInPath Item context,
                                                      @QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            return formFill.doFillRepositoryNameItems(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public ListBoxModel doFillServerIdItems(@AncestorInPath Item context,
                                                @QueryParameter String serverId) {
            return formFill.doFillServerIdItems(context, serverId);
        }

        @Override
        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item context,
                                                        @QueryParameter String baseUrl,
                                                        @QueryParameter String sshCredentialsId) {
            return formFill.doFillSshCredentialsIdItems(context, baseUrl, sshCredentialsId);
        }

        @Override
        @POST
        public FormValidation doTestConnection(@AncestorInPath Item context,
                                               @QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        public BitbucketClientFactoryProvider getBitbucketClientFactoryProvider() {
            return bitbucketClientFactoryProvider;
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket server";
        }

        @Override
        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return Collections.emptyList();
        }

        @Override
        public List<GitTool> getGitTools() {
            return Collections.emptyList();
        }

        public RetryingWebhookHandler getRetryingWebhookHandler() {
            return retryingWebhookHandler;
        }

        @Override
        public boolean getShowGitToolOptions() {
            return false;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            return gitScmSourceDescriptor.getTraitsDefaults();
        }

        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            return gitScmSourceDescriptor.getTraitsDescriptorLists();
        }

        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{UncategorizedSCMHeadCategory.DEFAULT, TagSCMHeadCategory.DEFAULT};
        }

        BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl,
                                                 @Nullable BitbucketTokenCredentials tokenCredentials) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(tokenCredentials));
        }

        Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }

        private BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }
    }
}
