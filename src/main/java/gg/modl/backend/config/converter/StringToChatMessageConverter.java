package gg.modl.backend.config.converter;

import gg.modl.backend.ticket.data.Ticket;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class StringToChatMessageConverter implements Converter<String, Ticket.ChatMessage> {

    @Override
    public Ticket.ChatMessage convert(String source) {
        return new Ticket.ChatMessage(source, null);
    }
}
