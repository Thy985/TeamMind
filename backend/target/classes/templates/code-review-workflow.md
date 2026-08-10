# Code Review Workflow

> Automated code review with multiple agents for comprehensive analysis

**Category:** Development  
**Public:** Yes  
**Usage Count:** 0

## Agents

- code-reviewer
- task-planner

## Workflow

1. **Initial Scan**
   - Code Reviewer analyzes the code
   - Identifies potential issues

2. **Task Breakdown**
   - Task Planner creates action items
   - Prioritizes fixes

3. **Report Generation**
   - Combined analysis report
   - Actionable recommendations

## Input

```json
{
  "repository": "string",
  "branch": "string",
  "files": ["string"]
}
```

## Output

```json
{
  "issues": [
    {
      "file": "string",
      "line": "number",
      "severity": "error|warning|info",
      "message": "string",
      "suggestion": "string"
    }
  ],
  "metrics": {
    "complexity": "number",
    "maintainability": "number",
    "coverage": "number"
  },
  "recommendations": ["string"]
}
```

---

Created: 2024-01-01  
Updated: 2024-01-01
