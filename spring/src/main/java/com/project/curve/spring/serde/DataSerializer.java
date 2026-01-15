package com.project.curve.spring.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//@Slf4j
//@NoArgsConstructor(access = AccessLevel.PRIVATE)
//public class DataSerializer {
//
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    public static String serialize(Object object) {
//        try {
//            return objectMapper.writeValueAsString(object);
//        } catch (Exception e) {
//            log.error(e.getMessage());
//            throw new RuntimeException(e);
//        }
//    }
//
//    public static <T> T deserialize(String json, Class<T> clazz) {
//        try {
//            return objectMapper.readValue(json, clazz);
//        } catch (Exception e) {
//            log.error(e.getMessage());
//            return null;
//        }
//    }
//}
