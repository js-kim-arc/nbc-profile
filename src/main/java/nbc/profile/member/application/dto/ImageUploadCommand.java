package nbc.profile.member.application.dto;

public record ImageUploadCommand(byte[] bytes, String contentType, String originalFilename) {
}
