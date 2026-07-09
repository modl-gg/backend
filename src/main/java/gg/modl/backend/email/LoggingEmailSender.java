package gg.modl.backend.email;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingEmailSender implements EmailSender {
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]+)\"");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        log.warn("[DEV EMAIL] to={} | subject=\"{}\" | {}", toEmail, subject, toReadable(htmlBody));
    }

    private String toReadable(String htmlBody) {
        StringBuilder links = new StringBuilder();
        Matcher hrefMatcher = HREF_PATTERN.matcher(htmlBody);
        while (hrefMatcher.find()) {
            links.append(' ').append(hrefMatcher.group(1));
        }

        String text = WHITESPACE_PATTERN.matcher(TAG_PATTERN.matcher(htmlBody).replaceAll(" ")).replaceAll(" ").trim();
        return links.length() > 0 ? text + " | links:" + links : text;
    }
}
