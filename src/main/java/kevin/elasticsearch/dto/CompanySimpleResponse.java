package kevin.elasticsearch.dto;

import kevin.elasticsearch.domain.Company;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySimpleResponse {
    private Long id;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ⭐ employees 필드 없음! (순환 방지)

    public static CompanySimpleResponse from(Company company) {
        return new CompanySimpleResponse(
                company.getId(),
                company.getName(),
                company.getAddress(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
