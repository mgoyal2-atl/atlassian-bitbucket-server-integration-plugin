package com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger
def f = namespace(lib.FormTagLib)

f.section() {

    f.entry(title: _("bitbucket.trigger.refTrigger"), field: "refTrigger", name: "refTrigger", inline: "true") {
        f.checkbox()
    }

    f.entry(title: _("bitbucket.trigger.pullRequestTrigger"), field: "pullRequestTrigger",  name: "pullRequestTrigger", inline: "true") {
        f.checkbox()
    }
}
