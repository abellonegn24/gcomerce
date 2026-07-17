package com.gadaaistar.admin.customers;

import java.util.concurrent.CompletionStage;

import com.gadaaistar.persistence.customer.Customer;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Sends B2B approval and rejection notification emails to the customer.
 * Lives in the admin API because these are admin-initiated actions.
 */
@ApplicationScoped
public class B2bNotificationMailService {

    private static final Logger LOG = Logger.getLogger(B2bNotificationMailService.class);

    @Inject ReactiveMailer mailer;

    @Location("mail/b2b-approved.html")  Template approvedHtml;
    @Location("mail/b2b-approved.txt")   Template approvedText;
    @Location("mail/b2b-rejected.html")  Template rejectedHtml;
    @Location("mail/b2b-rejected.txt")   Template rejectedText;

    @ConfigProperty(name = "quarkus.mailer.from") String fromAddress;
    @ConfigProperty(name = "gadaa.frontend.storefront-url") String storefrontUrl;

    public CompletionStage<Void> sendB2bApproved(Customer c) {
        var displayName = resolveDisplayName(c);
        var storeLink = storefrontUrl + "/shop";
        var subject = "Your Gadaa Istar wholesale account is approved";

        var mail = buildMail(c.email, subject, approvedHtml, approvedText,
                "displayName", displayName,
                "storeLink", storeLink);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent B2B approval email to %s", c.email))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send B2B approval email to %s", c.email))
                .subscribeAsCompletionStage();
    }

    public CompletionStage<Void> sendB2bRejected(Customer c, String reason) {
        var displayName = resolveDisplayName(c);
        var subject = "Update on your Gadaa Istar wholesale application";

        var mail = buildMail(c.email, subject, rejectedHtml, rejectedText,
                "displayName", displayName,
                "reason", reason);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent B2B rejection email to %s", c.email))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send B2B rejection email to %s", c.email))
                .subscribeAsCompletionStage();
    }

    // -------------------------------------------------------------------------

    private static String resolveDisplayName(Customer c) {
        if (c.firstName != null && !c.firstName.isBlank()) return c.firstName;
        if (c.companyName != null && !c.companyName.isBlank()) return c.companyName;
        return "there";
    }

    private Mail buildMail(String to, String subject,
                           Template htmlTpl, Template textTpl,
                           Object... vars) {
        var html = fill(htmlTpl, vars);
        var text = fill(textTpl, vars);
        return Mail.withHtml(to, subject, html.render())
                .setText(text.render())
                .setFrom(fromAddress);
    }

    private static TemplateInstance fill(Template tpl, Object... vars) {
        var instance = tpl.instance();
        for (int i = 0; i + 1 < vars.length; i += 2) {
            instance = instance.data(String.valueOf(vars[i]), vars[i + 1]);
        }
        return instance;
    }
}
