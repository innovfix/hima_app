# Report User Feature - Backend API Requirements

## Overview
This document outlines the backend API requirements for the **Report User** feature in the Hima app. This feature allows female users (creators) to report and block male users who exhibit inappropriate behavior.

---

## 1. API Endpoint: Get Report Reasons

### Endpoint
```
POST /api/report_reasons
```

### Purpose
Retrieve a list of predefined report reasons to display in the app's report dialog.

### Request Parameters
| Parameter | Type    | Required | Description                          |
|-----------|---------|----------|--------------------------------------|
| user_id   | Integer | Yes      | ID of the user requesting the list   |

### Request Example
```json
{
  "user_id": 123
}
```

### Response Format
```json
{
  "success": true,
  "message": "Report reasons fetched successfully",
  "data": [
    {
      "id": 1,
      "reason": "Fake profile",
      "requires_text": 0
    },
    {
      "id": 2,
      "reason": "Not replying",
      "requires_text": 0
    },
    {
      "id": 3,
      "reason": "Abusive behavior",
      "requires_text": 0
    },
    {
      "id": 4,
      "reason": "Attitude problem",
      "requires_text": 0
    },
    {
      "id": 5,
      "reason": "Asking for personal info",
      "requires_text": 0
    },
    {
      "id": 6,
      "reason": "Other",
      "requires_text": 1
    }
  ]
}
```

### Response Fields
| Field          | Type    | Description                                                    |
|----------------|---------|----------------------------------------------------------------|
| success        | Boolean | Indicates if the request was successful                        |
| message        | String  | Status message                                                 |
| data           | Array   | List of report reasons                                         |
| data[].id      | Integer | Unique identifier for the reason                               |
| data[].reason  | String  | Display text for the reason                                    |
| data[].requires_text | Integer | 1 = user must provide details, 0 = details optional    |

### Error Response
```json
{
  "success": false,
  "message": "Failed to fetch report reasons"
}
```

---

## 2. API Endpoint: Submit User Report

### Endpoint
```
POST /api/report_user
```

### Purpose
Submit a report against a user for inappropriate behavior.

### Request Parameters
| Parameter      | Type    | Required | Description                                  |
|----------------|---------|----------|----------------------------------------------|
| user_id        | Integer | Yes      | ID of the user submitting the report         |
| report_user_id | Integer | Yes      | ID of the user being reported                |
| reason_id      | Integer | Yes      | ID of the selected reason from report_reasons|
| reason_text    | String  | Conditional | Additional details (required if requires_text=1) |

### Request Example
```json
{
  "user_id": 123,
  "report_user_id": 456,
  "reason_id": 6,
  "reason_text": "User was asking for WhatsApp number repeatedly"
}
```

### Response Format
**Success Response:**
```json
{
  "success": true,
  "message": "Report submitted successfully"
}
```

**Error Response:**
```json
{
  "success": false,
  "message": "Failed to submit report"
}
```

### Validation Rules
1. `user_id` and `report_user_id` must be valid user IDs in the system
2. `reason_id` must exist in the `report_reasons` table
3. If `requires_text = 1` for the selected reason, `reason_text` must be provided and non-empty
4. `user_id` cannot report themselves (`user_id` ≠ `report_user_id`)

---

## 3. Database Schema

### Table: `report_reasons`

```sql
CREATE TABLE report_reasons (
    id INT PRIMARY KEY AUTO_INCREMENT,
    reason VARCHAR(255) NOT NULL,
    requires_text TINYINT(1) DEFAULT 0 COMMENT '1=required, 0=optional',
    is_active TINYINT(1) DEFAULT 1 COMMENT '1=active, 0=disabled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**Field Descriptions:**
- `id`: Unique identifier for each report reason
- `reason`: Text displayed to users (e.g., "Fake profile")
- `requires_text`: Flag indicating if additional details are mandatory
- `is_active`: Flag to enable/disable reasons without deleting them
- `created_at`: Record creation timestamp
- `updated_at`: Last modification timestamp

**Sample Data:**
```sql
INSERT INTO report_reasons (reason, requires_text, is_active) VALUES
('Fake profile', 0, 1),
('Not replying', 0, 1),
('Abusive behavior', 0, 1),
('Attitude problem', 0, 1),
('Asking for personal info', 0, 1),
('Other', 1, 1);
```

---

### Table: `user_reports`

```sql
CREATE TABLE user_reports (
    id INT PRIMARY KEY AUTO_INCREMENT,
    reporter_id INT NOT NULL COMMENT 'User who submitted the report',
    reported_user_id INT NOT NULL COMMENT 'User who was reported',
    reason_id INT NOT NULL,
    reason_text TEXT,
    status ENUM('pending', 'reviewed', 'resolved', 'dismissed') DEFAULT 'pending',
    admin_notes TEXT,
    reviewed_by INT DEFAULT NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (reason_id) REFERENCES report_reasons(id),
    INDEX idx_reporter (reporter_id),
    INDEX idx_reported (reported_user_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);
```

**Field Descriptions:**
- `id`: Unique report identifier
- `reporter_id`: User ID who submitted the report
- `reported_user_id`: User ID who was reported
- `reason_id`: Foreign key to `report_reasons.id`
- `reason_text`: Optional additional details provided by reporter
- `status`: Current status of the report
  - `pending`: Awaiting admin review
  - `reviewed`: Admin has reviewed but not acted
  - `resolved`: Action taken (e.g., user warned/banned)
  - `dismissed`: Report was invalid/no action needed
- `admin_notes`: Internal notes for admin use
- `reviewed_by`: Admin user ID who reviewed the report
- `reviewed_at`: Timestamp when report was reviewed
- `created_at`: When report was submitted
- `updated_at`: Last modification timestamp

---

## 4. Business Logic Recommendations

### Duplicate Report Prevention
- Consider preventing duplicate reports from the same user against the same target within a timeframe (e.g., 7 days)
- Query example:
```sql
SELECT COUNT(*) FROM user_reports 
WHERE reporter_id = ? 
AND reported_user_id = ? 
AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY);
```

### Auto-Action Thresholds
Implement automatic actions based on report counts:
- **3 reports within 24 hours**: Send warning notification to reported user
- **5 reports within 7 days**: Temporary suspension (24 hours)
- **10 reports total**: Permanent ban pending review

### Admin Dashboard
Provide additional admin endpoints for report management:
- `GET /admin/reports` - List all reports with filters (status, date range, reported user)
- `POST /admin/reports/:id/update` - Update report status and add admin notes
- `GET /admin/reports/statistics` - Get report statistics and trends

---

## 5. Additional API Endpoints (Future Enhancement)

### Get User Report History
```
POST /api/user_report_history
```
Get all reports submitted by or against a specific user.

**Request:**
```json
{
  "user_id": 123
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "reports_submitted": 5,
    "reports_received": 2,
    "recent_reports": [...]
  }
}
```

---

## 6. Testing Checklist

### API Testing
- [ ] Test `report_reasons` API returns all active reasons
- [ ] Test `report_user` API with valid data
- [ ] Test `report_user` API rejects duplicate reports within timeframe
- [ ] Test validation: user cannot report themselves
- [ ] Test validation: `reason_text` required when `requires_text = 1`
- [ ] Test validation: invalid `reason_id` returns error
- [ ] Test error handling for non-existent user IDs

### Database Testing
- [ ] Verify foreign key constraints work correctly
- [ ] Test indexes improve query performance
- [ ] Verify cascade behavior when deleting users
- [ ] Test ENUM values for status field

---

## 7. Security Considerations

1. **Rate Limiting**: Implement rate limiting to prevent abuse (max 5 reports per user per hour)
2. **SQL Injection**: Use parameterized queries for all database operations
3. **Authentication**: Verify user is logged in before accepting reports
4. **Authorization**: Ensure only female users can submit reports
5. **Data Sanitization**: Sanitize `reason_text` to prevent XSS attacks
6. **Audit Trail**: Maintain complete audit trail of all reports

---

## 8. API Error Codes

| HTTP Status | Error Code | Description                          |
|-------------|------------|--------------------------------------|
| 200         | -          | Success                              |
| 400         | 1001       | Invalid parameters                   |
| 400         | 1002       | Missing required field               |
| 400         | 1003       | Cannot report yourself               |
| 404         | 2001       | User not found                       |
| 404         | 2002       | Reason not found                     |
| 409         | 3001       | Duplicate report (recent)            |
| 429         | 4001       | Rate limit exceeded                  |
| 500         | 5000       | Internal server error                |

---

## 9. Deployment Notes

1. Run database migrations to create new tables
2. Seed `report_reasons` table with default data
3. Update API documentation
4. Monitor report submission rates in production
5. Set up admin notifications for high-priority reports

---

## 10. Contact

For questions or clarifications, contact:
- **Mobile Team**: [Your contact info]
- **Backend Team**: [Backend team contact]

---

**Document Version**: 1.0  
**Last Updated**: February 9, 2026  
**Prepared By**: Mobile Development Team
