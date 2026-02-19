package com.aigreentick.services.storage.constants;

public class ErrorMessages {
    private ErrorMessages() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

     // File validation errors
    public static final String FILE_EMPTY = "File cannot be empty or null";
    public static final String FILE_TOO_LARGE = "File size exceeds maximum allowed size of %d bytes";
    public static final String FILE_TOO_LARGE_FOR_TYPE = "File size %d bytes exceeds allowed max for %s (%d bytes)";
    public static final String INVALID_MIME_TYPE = "Content type '%s' is not supported";
    public static final String INVALID_FILENAME = "Invalid filename format. Filename cannot contain path traversal characters";
    public static final String MISSING_FILE_EXTENSION = "File must have a valid extension";
    public static final String MISSING_CONTENT_TYPE = "Content type is missing or empty";

    // Storage errors
    public static final String STORAGE_FULL = "Storage quota exceeded. Available: %d bytes, Required: %d bytes";
    public static final String STORAGE_UNAVAILABLE = "Storage service is temporarily unavailable";
    public static final String STORAGE_PATH_INVALID = "Invalid storage path configuration";

    // File operation errors
    public static final String FILE_NOT_FOUND = "Requested file not found: %s";
    public static final String FILE_UPLOAD_FAILED = "Failed to upload file: %s";
    public static final String FILE_DELETE_FAILED = "Failed to delete file: %s";
    public static final String FILE_READ_FAILED = "Failed to read file: %s";
    public static final String FILE_WRITE_FAILED = "Failed to write file: %s";
    public static final String FILE_CONVERSION_FAILED = "Failed to convert file: %s";

    // Versioning errors
    public static final String VERSION_NOT_FOUND = "Version %d not found for media ID: %d";
    public static final String VERSION_CREATE_FAILED = "Failed to create new version for media ID: %d";

    // Context errors
    public static final String USER_CONTEXT_MISSING = "User context is invalid or missing";
    public static final String ORG_CONTEXT_MISSING = "Organisation context is invalid or missing";

    // External service errors
    public static final String EXTERNAL_SERVICE_UNAVAILABLE = "%s is temporarily unavailable. Please try again later";
    public static final String EXTERNAL_SERVICE_ERROR = "Error communicating with %s: %s";

    // Rate limiting
    public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Try again in %d seconds";

    // Pagination errors
    public static final String INVALID_PAGE_NUMBER = "Page number must be >= 0";
    public static final String INVALID_PAGE_SIZE = "Page size must be between %d and %d";

    // WhatsApp/Facebook errors
    public static final String WHATSAPP_UPLOAD_FAILED = "Failed to upload media to WhatsApp: %s";
    public static final String FACEBOOK_API_ERROR = "Facebook API error: %s";
    public static final String MEDIA_TYPE_NOT_SUPPORTED = "Media type not supported by WhatsApp: %s";

    // Success messages
    public static final String UPLOAD_SUCCESS = "File uploaded successfully";
    public static final String DELETE_SUCCESS = "File deleted successfully";
    public static final String UPDATE_SUCCESS = "File updated successfully";


}
