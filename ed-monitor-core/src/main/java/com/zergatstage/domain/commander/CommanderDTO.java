package com.zergatstage.domain.commander;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor // Required by Jackson
@AllArgsConstructor // Required for builder pattern to work

public class CommanderDTO {
    private Long id;
    private String name;
    private boolean isDeleted;
    @JsonProperty("FID")
    private String FID;
}
