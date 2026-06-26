package gg.modl.backend.database;

import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Slf4j
@Configuration
public class TicketMongoEnumConfiguration {
    @Bean
    MongoCustomConversions ticketMongoCustomConversions() {
        return new MongoCustomConversions(List.of(
            new TicketCategoryWriteConverter(),
            new TicketCategoryReadConverter(),
            new TicketPriorityWriteConverter(),
            new TicketPriorityReadConverter(),
            new TicketStatusWriteConverter(),
            new TicketStatusReadConverter(),
            new AppealWorkflowStatusWriteConverter(),
            new AppealWorkflowStatusReadConverter(),
            new ChatMessageReadConverter()
        ));
    }

    @WritingConverter
    static class TicketCategoryWriteConverter implements Converter<TicketCategory, String> {
        @Override
        public String convert(TicketCategory source) {
            return source.getId();
        }
    }

    @ReadingConverter
    static class TicketCategoryReadConverter implements Converter<String, TicketCategory> {
        @Override
        public TicketCategory convert(String source) {
            try {
                return TicketCategory.fromCanonicalId(source);
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized stored ticket category '{}'; defaulting to SUPPORT", source);
                return TicketCategory.SUPPORT;
            }
        }
    }

    @WritingConverter
    static class TicketPriorityWriteConverter implements Converter<TicketPriority, String> {
        @Override
        public String convert(TicketPriority source) {
            return source.getId();
        }
    }

    @ReadingConverter
    static class TicketPriorityReadConverter implements Converter<String, TicketPriority> {
        @Override
        public TicketPriority convert(String source) {
            try {
                return TicketPriority.fromCanonicalId(source);
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized stored ticket priority '{}'; defaulting to NORMAL", source);
                return TicketPriority.NORMAL;
            }
        }
    }

    @WritingConverter
    static class TicketStatusWriteConverter implements Converter<TicketStatus, String> {
        @Override
        public String convert(TicketStatus source) {
            return source.getId();
        }
    }

    @ReadingConverter
    static class TicketStatusReadConverter implements Converter<String, TicketStatus> {
        @Override
        public TicketStatus convert(String source) {
            try {
                return TicketStatus.fromCanonicalId(source);
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized stored ticket status '{}'; defaulting to OPEN", source);
                return TicketStatus.OPEN;
            }
        }
    }

    @WritingConverter
    static class AppealWorkflowStatusWriteConverter implements Converter<AppealWorkflowStatus, String> {
        @Override
        public String convert(AppealWorkflowStatus source) {
            return source.getId();
        }
    }

    @ReadingConverter
    static class AppealWorkflowStatusReadConverter implements Converter<String, AppealWorkflowStatus> {
        @Override
        public AppealWorkflowStatus convert(String source) {
            try {
                return AppealWorkflowStatus.fromCanonicalId(source);
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized stored appeal workflow status '{}'; defaulting to null", source);
                return null;
            }
        }
    }

    @ReadingConverter
    static class ChatMessageReadConverter implements Converter<String, Ticket.ChatMessage> {
        @Override
        public Ticket.ChatMessage convert(String source) {
            return new Ticket.ChatMessage(source, null);
        }
    }
}
