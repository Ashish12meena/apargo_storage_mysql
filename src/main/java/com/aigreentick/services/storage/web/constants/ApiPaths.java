package com.aigreentick.services.storage.web.constants;

public class ApiPaths {
    private ApiPaths(){
        
    }
    
    // --- Field Names (if needed) ---
    public static final class Fields {
        private Fields() {
        }
        public static final String FILE = "file";
        public static final String STATUS = "status";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";
    }
    
     // --- API Paths ---
    public static final class Paths {
        private Paths() {
        }

        public static final String BASE = "/api/v1/media"; // base other are after this
        public static final String UPLOAD = "/upload";

        // --- Admin-specific paths ---
        public static final class Admin {
            private Admin() {
            }

            public static final String BASE = "/admin/v1/media";
        }
    }

    // --- Default Pagination Values ---
    public static final class Defaults {
        private Defaults() {
        }
    }

     // Messages ---
    public static final class Messages {
        private Messages() {
        }

        public static final String IMAGE_UPLOADED_SUCCESS = "image uploaded successfully";
        public static final String MEDIA_UPLOADED_SUCCESS = "media uploaded successfully";
    }
}