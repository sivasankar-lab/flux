package com.example.socialmedia_poc.config;

import com.example.socialmedia_poc.model.MetaConfig;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.*;

/**
 * JPA AttributeConverters for storing complex types as JSON TEXT columns in PostgreSQL.
 */
public class JpaConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    // ──────────────────────────────────────────────
    // Map converters
    // ──────────────────────────────────────────────

    @Converter
    public static class StringDoubleMapConverter implements AttributeConverter<Map<String, Double>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Double> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "{}"; }
        }
        @Override
        public Map<String, Double> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new HashMap<>() : MAPPER.readValue(dbData, new TypeReference<Map<String, Double>>() {}); }
            catch (Exception e) { return new HashMap<>(); }
        }
    }

    @Converter
    public static class StringIntegerMapConverter implements AttributeConverter<Map<String, Integer>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Integer> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "{}"; }
        }
        @Override
        public Map<String, Integer> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new HashMap<>() : MAPPER.readValue(dbData, new TypeReference<Map<String, Integer>>() {}); }
            catch (Exception e) { return new HashMap<>(); }
        }
    }

    @Converter
    public static class StringLongMapConverter implements AttributeConverter<Map<String, Long>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Long> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "{}"; }
        }
        @Override
        public Map<String, Long> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new HashMap<>() : MAPPER.readValue(dbData, new TypeReference<Map<String, Long>>() {}); }
            catch (Exception e) { return new HashMap<>(); }
        }
    }

    @Converter
    public static class StringStringMapConverter implements AttributeConverter<Map<String, String>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "{}"; }
        }
        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new HashMap<>() : MAPPER.readValue(dbData, new TypeReference<Map<String, String>>() {}); }
            catch (Exception e) { return new HashMap<>(); }
        }
    }

    // ──────────────────────────────────────────────
    // List converters
    // ──────────────────────────────────────────────

    @Converter
    public static class StringListConverter implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "[]"; }
        }
        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new ArrayList<>() : MAPPER.readValue(dbData, new TypeReference<List<String>>() {}); }
            catch (Exception e) { return new ArrayList<>(); }
        }
    }

    @Converter
    public static class IntegerListConverter implements AttributeConverter<List<Integer>, String> {
        @Override
        public String convertToDatabaseColumn(List<Integer> attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return "[]"; }
        }
        @Override
        public List<Integer> convertToEntityAttribute(String dbData) {
            try { return dbData == null ? new ArrayList<>() : MAPPER.readValue(dbData, new TypeReference<List<Integer>>() {}); }
            catch (Exception e) { return new ArrayList<>(); }
        }
    }

    // ──────────────────────────────────────────────
    // Complex object converters
    // ──────────────────────────────────────────────

    @Converter
    public static class MetaConfigConverter implements AttributeConverter<MetaConfig, String> {
        @Override
        public String convertToDatabaseColumn(MetaConfig attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return null; }
        }
        @Override
        public MetaConfig convertToEntityAttribute(String dbData) {
            try { return dbData == null ? null : MAPPER.readValue(dbData, MetaConfig.class); }
            catch (Exception e) { return null; }
        }
    }

    @Converter
    public static class GenerationContextConverter implements AttributeConverter<SeedWithMeta.GenerationContext, String> {
        @Override
        public String convertToDatabaseColumn(SeedWithMeta.GenerationContext attribute) {
            try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
            catch (Exception e) { return null; }
        }
        @Override
        public SeedWithMeta.GenerationContext convertToEntityAttribute(String dbData) {
            try { return dbData == null ? null : MAPPER.readValue(dbData, SeedWithMeta.GenerationContext.class); }
            catch (Exception e) { return null; }
        }
    }
}
