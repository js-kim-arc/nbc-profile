package nbc.profile.member.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nbc.profile.member.application.dto.MemberCreateCommand;

public record MemberCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Min(0) @Max(150) int age,
        @NotBlank @Pattern(regexp = "[EI][SN][TF][JP]") String mbti
) {

    public MemberCreateCommand toCommand() {
        return new MemberCreateCommand(name, age, mbti);
    }
}
