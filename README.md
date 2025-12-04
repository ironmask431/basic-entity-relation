# 양방향 매핑 순환 참조 방지 전략 학습 프로젝트

## 📋 프로젝트 개요

이 프로젝트는 JPA의 1:N 양방향 매핑 관계에서 발생할 수 있는 **순환 참조(Circular Reference)** 문제를 이해하고, 
**Simple DTO 패턴**을 통해 이를 해결하는 방법을 학습하기 위한 실습 프로젝트입니다.

### 기술 스택
- **Java 21**
- **Spring Boot 4.0.0**
- **Spring Data JPA**
- **MySQL 8.0**
- **Lombok**
- **Gradle**

---

## 🏗️ 프로젝트 구조

```
src/main/java/kevin/elasticsearch/
├── config/
│   └── JpaConfig.java              # JPA 설정
├── controller/
│   ├── CompanyController.java      # 회사 CRUD API
│   └── EmployeeController.java     # 직원 CRUD API
├── domain/
│   ├── Company.java                # 회사 엔티티 (1)
│   └── Employee.java               # 직원 엔티티 (N)
├── dto/
│   ├── CompanyRequest.java         # 회사 생성/수정 요청 DTO
│   ├── CompanyResponse.java        # 회사 조회 응답 DTO (Full)
│   ├── CompanySimpleResponse.java  # 회사 간략 응답 DTO (순환 방지용)
│   ├── EmployeeRequest.java        # 직원 생성/수정 요청 DTO
│   ├── EmployeeResponse.java       # 직원 조회 응답 DTO (Full)
│   └── EmployeeSimpleResponse.java # 직원 간략 응답 DTO (순환 방지용)
├── repository/
│   ├── CompanyRepository.java      # 회사 레포지토리
│   └── EmployeeRepository.java     # 직원 레포지토리
└── service/
    ├── CompanyService.java         # 회사 비즈니스 로직
    └── EmployeeService.java        # 직원 비즈니스 로직
```

---

## 🔄 양방향 매핑 구조

### 엔티티 관계

```java
@Entity
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String address;
    
    // ⭐ 양방향 매핑: Company → Employee
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<Employee> employees = new ArrayList<>();
}

@Entity
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String email;
    private String position;
    
    // ⭐ 양방향 매핑: Employee → Company
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;
}
```

### ERD
```
┌─────────────┐         ┌──────────────┐
│   Company   │ 1     N │   Employee   │
├─────────────┤◄────────┤──────────────┤
│ id (PK)     │         │ id (PK)      │
│ name        │         │ name         │
│ address     │         │ email        │
│ created_at  │         │ position     │
│ updated_at  │         │ company_id   │
└─────────────┘         │ created_at   │
                        │ updated_at   │
                        └──────────────┘
```

---

## ⚠️ 순환 참조 문제

### 문제 상황

양방향 매핑에서 Entity를 직접 JSON으로 변환하면 **순환 참조**가 발생합니다.

```java
// ❌ Entity를 직접 반환하는 경우
@GetMapping("/{id}")
public Company getCompany(@PathVariable Long id) {
    return companyRepository.findById(id).orElseThrow();
}
```

### 순환 참조 발생 과정

```
Company 조회 시도
    ↓
Company → employees (List<Employee>)
    ↓
Employee → company (Company)
    ↓
Company → employees (List<Employee>)
    ↓
Employee → company (Company)
    ↓
... 무한 반복! 💥 StackOverflowError
```

### JSON 직렬화 시 문제

```json
{
  "id": 1,
  "name": "테크컴퍼니",
  "employees": [
    {
      "id": 1,
      "name": "김철수",
      "company": {
        "id": 1,
        "name": "테크컴퍼니",
        "employees": [
          {
            "id": 1,
            "name": "김철수",
            "company": {
              // 무한 반복...
            }
          }
        ]
      }
    }
  ]
}
```

---

## ✅ Simple DTO 패턴을 통한 해결

### 핵심 전략

**"Simple DTO에는 관계 필드를 포함하지 않는다"**

양방향 관계에서 한쪽 방향으로 조회할 때, 반대편 객체는 **Simple 버전**을 사용하여 순환을 끊습니다.

### DTO 구조

#### 1. CompanyResponse (Full DTO)
```java
public class CompanyResponse {
    private Long id;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ⭐ EmployeeSimpleResponse 사용 (company 필드 없음)
    private List<EmployeeSimpleResponse> employees;
}
```

#### 2. EmployeeSimpleResponse (Simple DTO)
```java
public class EmployeeSimpleResponse {
    private Long id;
    private String name;
    private String email;
    private String position;
    
    // ⭐ company 필드 없음! → 순환 끊김 ✂️
}
```

#### 3. EmployeeResponse (Full DTO)
```java
public class EmployeeResponse {
    private Long id;
    private String name;
    private String email;
    private String position;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ⭐ CompanySimpleResponse 사용 (employees 필드 없음)
    private CompanySimpleResponse company;
}
```

#### 4. CompanySimpleResponse (Simple DTO)
```java
public class CompanySimpleResponse {
    private Long id;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ⭐ employees 필드 없음! → 순환 끊김 ✂️
}
```

### 순환 참조 방지 원리

```
CompanyResponse 조회
    ↓
employees: List<EmployeeSimpleResponse>
    ↓
EmployeeSimpleResponse에는 company 필드 없음
    ↓
✅ 여기서 끝! 순환 발생 안 함!

EmployeeResponse 조회
    ↓
company: CompanySimpleResponse
    ↓
CompanySimpleResponse에는 employees 필드 없음
    ↓
✅ 여기서 끝! 순환 발생 안 함!
```

---

## 🚀 API 명세 및 실행 예시

### 1. 회사 생성

**Request**
```http
POST http://localhost:8080/api/companies
Content-Type: application/json

{
  "name": "테크 컴퍼니",
  "address": "서울시 강남구 테헤란로 123"
}
```

**Response (201 Created)**
```json
{
  "id": 1,
  "name": "테크 컴퍼니",
  "address": "서울시 강남구 테헤란로 123",
  "createdAt": "2024-12-04T10:00:00",
  "updatedAt": "2024-12-04T10:00:00",
  "employees": null
}
```

### 2. 직원 생성 (회사 지정)

**Request**
```http
POST http://localhost:8080/api/employees
Content-Type: application/json

{
  "name": "김철수",
  "email": "kim@example.com",
  "position": "개발자",
  "companyId": 1
}
```

**Response (201 Created)**
```json
{
  "id": 1,
  "name": "김철수",
  "email": "kim@example.com",
  "position": "개발자",
  "company": {
    "id": 1,
    "name": "테크 컴퍼니",
    "address": "서울시 강남구 테헤란로 123",
    "createdAt": "2024-12-04T10:00:00",
    "updatedAt": "2024-12-04T10:00:00"
  },
  "createdAt": "2024-12-04T10:05:00",
  "updatedAt": "2024-12-04T10:05:00"
}
```

**✅ 순환 참조 방지 확인**
- `company` 필드에 `CompanySimpleResponse`가 사용됨
- `CompanySimpleResponse`에는 `employees` 필드가 없음
- ✂️ 여기서 순환이 끊김!

### 3. 회사 조회 (직원 목록 포함)

**Request**
```http
GET http://localhost:8080/api/companies/1
```

**Response (200 OK)**
```json
{
  "id": 1,
  "name": "테크 컴퍼니",
  "address": "서울시 강남구 테헤란로 123",
  "createdAt": "2024-12-04T10:00:00",
  "updatedAt": "2024-12-04T10:00:00",
  "employees": [
    {
      "id": 1,
      "name": "김철수",
      "email": "kim@example.com",
      "position": "개발자"
    },
    {
      "id": 2,
      "name": "이영희",
      "email": "lee@example.com",
      "position": "디자이너"
    }
  ]
}
```

**✅ 순환 참조 방지 확인**
- `employees` 필드에 `EmployeeSimpleResponse` 리스트가 사용됨
- `EmployeeSimpleResponse`에는 `company` 필드가 없음
- ✂️ 여기서 순환이 끊김!

### 4. 직원 조회

**Request**
```http
GET http://localhost:8080/api/employees/1
```

**Response (200 OK)**
```json
{
  "id": 1,
  "name": "김철수",
  "email": "kim@example.com",
  "position": "개발자",
  "company": {
    "id": 1,
    "name": "테크 컴퍼니",
    "address": "서울시 강남구 테헤란로 123",
    "createdAt": "2024-12-04T10:00:00",
    "updatedAt": "2024-12-04T10:00:00"
  },
  "createdAt": "2024-12-04T10:05:00",
  "updatedAt": "2024-12-04T10:05:00"
}
```

**✅ 순환 참조 방지 확인**
- `company` 필드가 `CompanySimpleResponse`로 변환됨
- `employees` 필드가 포함되지 않아 순환 발생 안 함!

---

## 📊 순환 참조 방지 검증

### 호출 흐름 분석

#### Company 조회 시
```
1. GET /api/companies/1
    ↓
2. CompanyResponse 생성
    ↓
3. employees → List<EmployeeSimpleResponse> 변환
    ↓
4. EmployeeSimpleResponse (company 필드 없음)
    ↓
5. ✅ 끝! 2단계에서 종료
```

#### Employee 조회 시
```
1. GET /api/employees/1
    ↓
2. EmployeeResponse 생성
    ↓
3. company → CompanySimpleResponse 변환
    ↓
4. CompanySimpleResponse (employees 필드 없음)
    ↓
5. ✅ 끝! 2단계에서 종료
```

### 핵심 포인트
- **CompanySimpleResponse**: `employees` 필드 ❌
- **EmployeeSimpleResponse**: `company` 필드 ❌
- 두 Simple DTO 모두 관계 필드가 없어 순환이 **절대 발생하지 않음**

---

## 🎯 학습 포인트

### 1. 양방향 매핑의 문제점
- Entity 간 순환 참조로 인한 StackOverflowError
- JSON 직렬화 시 무한 루프
- 불필요한 데이터 중복 로딩

### 2. Simple DTO 패턴의 장점
- ✅ 순환 참조 완전 차단
- ✅ 필요한 데이터만 선택적 노출
- ✅ API 응답 크기 최적화
- ✅ 명확한 계층 구조

### 3. 실무 적용 원칙
- Full Response에서 관계를 표현할 때는 항상 Simple 버전 사용
- Simple DTO는 자기 자신의 기본 정보만 포함
- 관계 필드는 절대 포함하지 않음

---

## 🛠️ 실행 방법

### 1. 데이터베이스 준비
```sql
CREATE DATABASE logback_elasticsearch_kibana;
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. API 테스트
Postman이나 curl을 사용하여 위의 API 예시를 테스트합니다.

---

## 💡 결론

양방향 매핑은 편리하지만 순환 참조 문제를 야기할 수 있습니다. 
**Simple DTO 패턴**을 통해 이 문제를 해결할 수 있으며, 
실무에서는 이러한 패턴을 일관되게 적용하여 안정적인 API를 구축할 수 있습니다.
핵심은 **"Simple DTO에는 관계 필드를 포함하지 않는다"**는 단순한 규칙입니다.

그러나 양방향 매핑과 simpleDTO 도 너무 관계가 많아지면 관리가 복잡해지고, 
그로인한 실수로 순환참조오류가 다시 발생 할 위험이 있습니다. 
실무나 복잡한 대규모 DB 모델링에서는 단방향 연관관계(ManyToOne)를 기본으로하고, 
1도메인에서 N도메인을 빈번하게 조회하는 경우에 한해서 양방향 매핑과 SimpleDTO
전략을 사용하면 좋을 것 같습니다. 
