package com.aigreentick.services.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseMessage<T> {
   private String status;
   private String message;
   private T data;

  }
