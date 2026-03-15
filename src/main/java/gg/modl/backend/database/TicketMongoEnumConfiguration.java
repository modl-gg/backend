package gg.modl.backend.database;

import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

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
            new AppealWorkflowStatusReadConverter()
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
            return TicketCategory.fromCanonicalId(source);
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
            return TicketPriority.fromCanonicalId(source);
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
            return TicketStatus.fromCanonicalId(source);
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
            return AppealWorkflowStatus.fromCanonicalId(source);
        }
    }
}
