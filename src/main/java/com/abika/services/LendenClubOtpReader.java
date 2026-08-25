package com.abika.services;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;

import javax.mail.*;
import javax.mail.search.*;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.resilience4j.retry.*;



public class LendenClubOtpReader {

    public static String getOtpFromEmail(String username, String password) throws Exception {

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .intervalFunction(
                        IntervalFunction.ofExponentialBackoff(
                                5000,
                                2.0
                        )
                )
                // Retry only when cause is OtpEmailNotFoundException
                .retryOnException(ex -> ex.getCause() instanceof OtpEmailNotFoundException)
                .build();

        Retry retry = Retry.of("otp-fetch", config);

// -------- LOG LISTENERS --------
        retry.getEventPublisher()
                .onRetry(event -> {
                    System.out.println("Retry attempt: " + event.getNumberOfRetryAttempts() +
                            " — Reason: " + event.getLastThrowable().getCause().getMessage());
                })
                .onSuccess(event -> {
                    System.out.println("OTP fetch SUCCESS");
                })
                .onError(event -> {
                    System.out.println("OTP fetch FAILED: " +
                            event.getLastThrowable().getCause().getMessage());
                });

        Supplier<String> decoratedSupplier =
                Retry.decorateSupplier(retry, () -> {
                    try {
                        return tryFetchOtp(username, password);
                    } catch (Exception e) {
                        throw new RuntimeException(e);  // <-- Fix for "Unhandled Exception"
                    }
                });

        try {
            return decoratedSupplier.get();
        } catch (Exception ex) {
            throw new Exception("Failed to fetch OTP after retries", ex);
        }
    }


    private static String tryFetchOtp(String username, String password) throws Exception {

        String host = "imap.gmail.com";
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");

        Session session = Session.getDefaultInstance(properties);
        Store store = session.getStore("imaps");
        store.connect(host, username, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        FlagTerm unseenFlag = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        FromStringTerm fromAddress = new FromStringTerm("noreply@lendenclub.com");
        SubjectTerm subjectTerm = new SubjectTerm("LendenClub | OTP for Login");

        SearchTerm searchTerm = new AndTerm(new SearchTerm[]{ unseenFlag, fromAddress, subjectTerm });

        Message[] messages = inbox.search(searchTerm);

        if (messages.length == 0) {
            inbox.close(false);
            store.close();
            throw new OtpEmailNotFoundException("No unread OTP email found");
        }

        Message recent = messages[messages.length - 1];
        String content = getTextFromMessage(recent);

        String otp = extractOtp(content);
        if (otp == null) {
            inbox.close(false);
            store.close();
            throw new Exception("OTP not found inside email content"); // No retry for this
        }
        recent.setFlag(Flags.Flag.SEEN, true);
        inbox.close(true);
        store.close();
        return otp;
    }

    private static String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("text/html")) {
            // Directly return HTML as string (you can strip tags if needed)
            String html = (String) message.getContent();
            return org.jsoup.Jsoup.parse(html).text(); // convert to plain text
        } else if (message.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) message.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    return part.getContent().toString(); // Prefer plain text
                } else if (part.isMimeType("text/html")) {
                    String html = (String) part.getContent();
                    return org.jsoup.Jsoup.parse(html).text();
                }
            }
            return result.toString();
        }
        return "";
    }

    // Extract 6-digit OTP from text
    private static String extractOtp(String text) {
        Pattern pattern = Pattern.compile("\\b\\d{6}\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}

