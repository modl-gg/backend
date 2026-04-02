package gg.modl.backend.infrastructure.proto;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@ControllerAdvice
public class ProtoValidationAdvice extends RequestBodyAdviceAdapter {

    private final Validator validator = new Validator();

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return Message.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof Message protoMessage) {
            try {
                ValidationResult result = validator.validate(protoMessage);
                if (!result.isSuccess()) {
                    throw new ProtoValidationException(result);
                }
            } catch (ValidationException e) {
                throw new IllegalArgumentException("Proto validation failed: " + e.getMessage(), e);
            }
        }
        return body;
    }
}
