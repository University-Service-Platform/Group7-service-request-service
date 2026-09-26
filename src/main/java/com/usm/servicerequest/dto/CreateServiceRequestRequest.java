package com.usm.servicerequest.dto;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateServiceRequestRequest(

        @NotNull(message = "category is required")
        RequestCategory category,

        @NotBlank(message = "location is required")
        @Size(max = 255)
        String location,

        @NotNull(message = "priority is required")
        RequestPriority priority,

        @NotBlank(message = "description is required")
        String description,

        @Size(max = 500)
        String attachmentReference
) {
}
