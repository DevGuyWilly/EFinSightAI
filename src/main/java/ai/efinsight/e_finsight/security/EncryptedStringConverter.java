package ai.efinsight.e_finsight.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

// Encrypts a String column at rest with TokenCipher. A Spring bean: Hibernate gets converters from Spring's
// bean container in Spring Boot, so the cipher (and its key) can be injected.
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final TokenCipher tokenCipher;

    public EncryptedStringConverter(TokenCipher tokenCipher) {
        this.tokenCipher = tokenCipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return tokenCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return tokenCipher.decrypt(dbData);
    }
}
