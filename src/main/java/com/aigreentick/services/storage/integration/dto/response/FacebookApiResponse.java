package com.aigreentick.services.storage.integration.dto.response;

import lombok.Data;

@Data
public class FacebookApiResponse<T> {
   private boolean success;
   private T data;
   private String errorMessage;
   private int statusCode;

   public static <T> FacebookApiResponse<T> success(T data, int statusCode) {
      FacebookApiResponse<T> response = new FacebookApiResponse<>();
      response.success = true;
      response.data = data;
      response.statusCode = statusCode;
      return response;
   }

   public static <T> FacebookApiResponse<T> error(String errorMessage, int statusCode) {
      FacebookApiResponse<T> response = new FacebookApiResponse<>();
      response.success = false;
      response.errorMessage = errorMessage;
      response.statusCode = statusCode;
      return response;
   }
}
