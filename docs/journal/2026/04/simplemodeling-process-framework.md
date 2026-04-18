# SimpleModeling Essence Method Framework

status=draft

---

# 0. Purpose

This document defines a working framework for building a SimpleModeling development method based on Essence.

It is intended as a single-file working document for discussion with ChatGPT and for iterative refinement.

The document covers:

- Method meta-model
- Practice Group classification
- Practice definitions
- Concrete Method composition
- Notes and open questions

---

# 1. Source Material

This document reconstructs the SimpleModeling development practices from the following source material:

- `process-overview_fixed.pptx`: overview and base process definition
- `process-usecase_fixed.pptx`: requirement, Use Case Lite, BDD, and Scrum Solo details
- `process-analysis_fixed.pptx`: analysis model and Identify Subsystems details
- `process-design_fixed.pptx`: design model and Make Cloud Native CBD details
- `process-implementation_fixed.pptx`: implementation, User Environment Lite, and CI/CD Pipeline details
- `process-text_fixed.pptx`: test policy, BDD/TDD, testability, and CI/CD Pipeline details

Reference policy:

- The canonical Essence reference is The Essentials of Modern Software Engineering: Free the Practices from the Method Prisons!
- The Essence of Software Engineering: Applying the SEMAT Kernel is treated as background reference
- The PowerPoint source material is treated as the SimpleModeling reconstruction source

---

# 2. Core Definition

## Method

Method
  = Kernel
  + selected Practices

- Kernel is fixed
- Kernel means Essence Kernel
- Practices are selected and composed into a Method
- Practice Groups are not directly composed into a Method

## Kernel

Kernel
  = Essence Kernel

- The Kernel provides the common foundation
- The Kernel is always present in a Method
- This document does not redefine the Kernel

Kernel Alphas:

- Opportunity
- Stakeholders
- Requirements
- Software System
- Work
- Team
- Way of Working

## Practice

Practice
  = reusable unit of method composition

A Practice may define:

- Alphas
- Activity Spaces
- Activities
- Work Products
- Competencies
- Patterns
- Guidance
- Checkpoints

Rules:

- Practices are the unit of composition
- Practices are applied to the Kernel
- Practices should be independently selectable
- Practices should avoid overlapping responsibilities at composition level

## Practice Group

Practice Group
  = flat classification of Practices

Rules:

- Practice Group is a SimpleModeling classification concept, not an Essence standard element
- Practice Groups are peer-level
- Practice Groups are classification only
- Practice Groups are not part of a Method
- Practice Groups are used as a toolbox for selecting Practices
- Practice Groups must remain flat

---

# 3. Practice Groups

## Essence Practice Group

- Use Case Lite Practice
- User Story Lite Practice
- Scrum Lite Practice
- Microservices Lite Practice

### Notes

- See the canonical reference for detailed definitions of these Practices
- Keep this group stable and update it only when new Essence reference facts are identified
- Use Case Lite Practice and User Story Lite Practice are alternative practices
- They operate on the same Requirements Alpha
- A method typically selects one of them

---

## Solo Development Practice Group

- Scrum Solo Practice

### Notes

- Keep this group for one-person development
- One-person development may become more practical in the AI-assisted development era

---

## SimpleModeling Practice Group

- Business Modeling Solo Practice
- Cloud Native CBD Practice
- BDD with Use-Case Slice Lite Practice
- User Environment Lite Practice
- CI/CD Pipeline Practice
- DevOpsSec Solo Practice

### Notes

- Reconstruct this group based on current software engineering technology
- Treat the lecture practices as source material, not as a fixed final structure

---

## SimpleModeling with CML Practice Group

- CML Use-Case Modeling Practice
- CML Modeling Practice
- Code Generation Practice

### Notes

- Reconstruct this group based on current CML and model-driven development technology
- Keep CML-based use-case modeling independent from Use Case Lite Practice

---

# 4. Concrete Method

## Solo SimpleModeling Development Method

Solo SimpleModeling Development Method
  = Kernel
  + selected Practices

### Kernel

- Essence Kernel

### Selected Practices

- Use Case Lite Practice
- Scrum Solo Practice
- Business Modeling Solo Practice
- Cloud Native CBD Practice
- BDD with Use-Case Slice Lite Practice
- User Environment Lite Practice
- CI/CD Pipeline Practice
- DevOpsSec Solo Practice

### Selection Notes

- This method selects Use Case Lite Practice instead of User Story Lite Practice
- Scrum Solo Practice is selected instead of Scrum Lite Practice
- Cloud Native CBD Practice provides analysis, design, and implementation activities
- CI/CD Pipeline Practice provides build, test, deploy, and delivery pipeline activities
- CML practices are not selected in the base method

---

# 5. Practice Definitions

## Use Case Lite Practice

Source:

- Essence canonical reference
- `process-usecase_fixed.pptx`

Purpose:

- Capture functional requirements as Use Cases
- Clarify actors, goals, and system responsibilities
- Split Use Cases into implementable and testable Use-Case Slices

Primary Alphas:

- Requirements
- Use Case
- Use-Case Slice

Activity Spaces:

- Understand Requirements
- Test the System

Activities:

- Find Actors and Use Cases
- Slice the Use Case
- Prepare a Use-Case Slice
- Test a Use-Case Slice

Work Products:

- Use-Case Model
- Use-Case Narrative
- Use-Case Slice
- Use-Case Slice Test Case

Notes:

- Use-Case Slice is part of Use Case Lite Practice in this document
- Use Case Lite Practice is an alternative to User Story Lite Practice

---

## User Story Lite Practice

Source:

- Essence canonical reference

Purpose:

- Capture requirements as User Stories
- Provide a backlog-oriented planning and delivery unit

Primary Alpha:

- Requirements

Activity Spaces:

- Understand Requirements
- Prepare to Do the Work

Activities:

- Find Stories
- Slice Stories
- Prepare a Story
- Accept a Story

Work Products:

- Product Backlog
- User Story
- Acceptance Criterion

Notes:

- User Story Lite Practice is an alternative to Use Case Lite Practice

---

## Scrum Lite Practice

Source:

- Essence canonical reference
- `process-usecase_fixed.pptx`

Purpose:

- Organize team-based iterative development
- Provide events, accountabilities, and artifacts for empirical process control

Primary Alphas:

- Way of Working
- Team
- Work
- Sprint
- Daily

Activity Spaces:

- Prepare to Do the Work
- Coordinate Activity
- Track Progress
- Support the Team

Activities:

- Sprint Planning
- Daily Scrum
- Sprint Review
- Sprint Retrospective
- Product Backlog Refinement

Work Products:

- Product Backlog
- Sprint Backlog
- Increment

---

## Scrum Solo Practice

Source:

- `process-overview_fixed.pptx`
- `process-usecase_fixed.pptx`

Purpose:

- Adapt Scrum Lite for solo development
- Preserve planning, review, and improvement without requiring team ceremonies

Primary Alphas:

- Way of Working
- Work

Activity Spaces:

- Prepare to Do the Work
- Coordinate Activity
- Track Progress
- Support the Team

Activities:

- Sprint Planning Solo
- Daily Scrum Solo
- Sprint Review Solo
- Sprint Retrospective Solo

Work Products:

- Product Backlog
- Sprint Backlog Solo
- Increment
- Review Notes
- Retrospective Notes

Notes:

- This Practice is classified under Solo Development Practice Group
- This Practice can replace Scrum Lite Practice in a solo Method

---

## Microservices Lite Practice

Source:

- Essence canonical reference

Purpose:

- Structure the software system as independently deployable services
- Align service boundaries with business capabilities

Primary Alphas:

- Software System
- Microservice

Activity Spaces:

- Shape the System
- Implement the System
- Deploy the System

Activities:

- Identify Microservices
- Make Evolvable
- Evolve Microservice

Work Products:

- Microservice Design
- Design Model
- Microservice Build and Deployment Script
- Microservice Test Case

---

## Business Modeling Solo Practice

Source:

- `process-overview_fixed.pptx`

Purpose:

- Organize the usage context of the target IT system
- Provide lightweight business modeling for solo development

Primary Alphas:

- Opportunity
- Stakeholders
- Requirements

Activity Spaces:

- Explore Possibilities
- Understand Stakeholder Needs

Activities:

- Explore Context
- Identify Stakeholders and Actors
- Model Business Flow
- Clarify Business Goal

Work Products:

- Business Context Note
- Business Flow Model
- Stakeholder List
- Actor List

---

## Cloud Native CBD Practice

Source:

- `process-overview_fixed.pptx`
- `process-analysis_fixed.pptx`
- `process-design_fixed.pptx`
- `process-implementation_fixed.pptx`

Purpose:

- Apply component-based development to cloud-native application development
- Connect requirement, analysis, design, implementation, and test work through component boundaries
- Support Event-Centric, CQRS, Eventually Consistency, and Cloud Native Component Framework based development

Primary Alphas:

- Requirements
- Software System
- Work

Activity Spaces:

- Understand Requirements
- Shape the System
- Implement the System
- Test the System
- Deploy the System

Activities:

- Identify Subsystems
- Make Cloud Native CBD
- Evolve Cloud Native CBD

Work Products:

- Analysis Model
- Subsystem Model
- Component Model
- Domain Model
- Design Model
- Source Code
- Test Case
- Deployment Unit

Notes:

- Cloud Native CBD is the main SimpleModeling Practice for analysis, design, and implementation
- BDD/TDD specifications created from requirements and analysis are carried forward through implementation

---

## Identify Subsystems Activity

Source:

- `process-analysis_fixed.pptx`

Parent Practice:

- Cloud Native CBD Practice

Purpose:

- Create the analysis model
- Split the system into subsystems and clarify subsystem boundaries

Activity Spaces:

- Understand Requirements
- Shape the System

Activities:

- Analyze Use-Case Scenarios
- Distill Subsystems
- Analyze a Domain Model

Work Products:

- Analysis Model
- Boundary Object Specification
- Subsystem Candidate List
- Domain Model
- Statechart
- Analysis TDD Specification

Notes:

- UML is used only where it is useful
- Scala code and TDD specifications may be used as lightweight analysis artifacts
- Requirement BDD specifications should continue to run after analysis changes

---

## Make Cloud Native CBD Activity

Source:

- `process-design_fixed.pptx`

Parent Practice:

- Cloud Native CBD Practice

Purpose:

- Create the design model according to Cloud Native CBD
- Convert platform-independent subsystem specifications into platform-specific subsystem specifications

Activity Spaces:

- Shape the System

Activities:

- Define the System Architecture
- Finalize the SubSystem Specification
- Develop the Internal Structure of the SubSystem
- Develop the Domain Model

Work Products:

- System Architecture
- SubSystem Specification
- Interface Design
- Internal Structure Model
- Domain Model
- Component Specification

Notes:

- Design concerns include Event-Centric architecture, CQRS, Eventually Consistency, and Cloud Native Component Framework alignment
- TDD continues to connect specification and implementation after the analysis model

---

## Evolve Cloud Native CBD Activity

Source:

- `process-implementation_fixed.pptx`
- `process-text_fixed.pptx`

Parent Practice:

- Cloud Native CBD Practice

Purpose:

- Implement the software based on the Cloud Native CBD design model
- Continue BDD/TDD specifications as living specifications

Activity Spaces:

- Implement the System
- Test the System
- Deploy the System

Activities:

- Implement Component Logic
- Add TDD Specification
- Continue Requirement BDD Specification
- Package Component
- Integrate with CI/CD Pipeline

Work Products:

- Source Code
- TDD Specification
- BDD Specification
- Component Package
- Test Result

Notes:

- Development proceeds with TDD
- Specification changes should be reflected in BDD/TDD specifications

---

## BDD with Use-Case Slice Lite Practice

Source:

- `process-usecase_fixed.pptx`
- `process-text_fixed.pptx`

Purpose:

- Realize Use-Case Slices as BDD specifications
- Keep requirement specification and test case closely connected
- Provide living documentation through executable specifications

Primary Alphas:

- Requirements
- Software System

Activity Spaces:

- Understand Requirements
- Test the System

Activities:

- Make a BDD Specification
- Execute BDD Specification
- Maintain BDD Specification

Work Products:

- BDD Specification
- Use-Case Slice Test Case
- Test Result
- Living Documentation

Notes:

- This Practice uses outputs from Use Case Lite Practice
- Test a Use-Case Slice joins back into Use Case Lite Practice

---

## User Environment Lite Practice

Source:

- `process-implementation_fixed.pptx`
- `process-text_fixed.pptx`

Purpose:

- Prepare the user environment needed to use and validate the system
- Maintain documents that support user operation and stakeholder review

Primary Alphas:

- Stakeholders
- Software System

Activity Spaces:

- Ensure Stakeholder Satisfaction
- Use the System
- Operate the System

Activities:

- Make Documents
- Evolve Documents

Work Products:

- User Guide
- Reference Manual
- Operation Manual
- Specification Document

Notes:

- Source material uses both User Environment Solo, User Environment Light, and User Environment Lite
- This document standardizes the Practice name as User Environment Lite Practice

---

## CI/CD Pipeline Practice

Source:

- `process-overview_fixed.pptx`
- `process-implementation_fixed.pptx`
- `process-text_fixed.pptx`

Purpose:

- Build and operate the pipeline required to build, test, package, deploy, and deliver the system
- Keep BDD/TDD tests continuously executed through the pipeline

Primary Alphas:

- Software System
- Work
- Way of Working

Activity Spaces:

- Implement the System
- Test the System
- Deploy the System
- Track Progress

Activities:

- Ensure CI/CD Pipeline to Test
- Ensure CI/CD Pipeline to Deploy
- Ensure CI/CD Pipeline to Deliver

Work Products:

- Build Definition
- Test Script
- Pipeline Definition
- Docker Image
- Serverless Artifact
- Deployment Result
- Delivery Configuration

Notes:

- The CI part ensures tests run and quality is checked continuously
- The CD part deploys Docker images and serverless artifacts
- Delivery to multiple targets may be added after deployment is stable

---

## DevOpsSec Solo Practice

Source:

- `process-overview_fixed.pptx`
- `process-implementation_fixed.pptx`

Purpose:

- Provide a lightweight solo-development form of DevOpsSec
- Keep operations, security, observability, and maintenance concerns visible

Primary Alphas:

- Software System
- Way of Working
- Work

Activity Spaces:

- Operate the System
- Support the Team
- Track Progress

Activities:

- Prepare Observability
- Apply Security Check
- Maintain Operational Checklist
- Feed Operational Findings into Backlog

Work Products:

- Operational Checklist
- Security Checklist
- Observability Note
- Improvement Backlog Item

Notes:

- Source material mentions GitHub, AWS, OpenTelemetry, Zero Trust, Immutable Infrastructure, and 3R as implementation direction

---

## CML Use-Case Modeling Practice

Purpose:

- Model Use Cases and Use-Case Slices with CML
- Keep CML-based use-case modeling independent from Use Case Lite Practice

Primary Alpha:

- Requirements

Activity Spaces:

- Understand Requirements

Activities:

- Write CML Use-Case Model
- Write CML Use-Case Slice
- Validate CML Requirement Model

Work Products:

- CML Use-Case Model
- CML Use-Case Slice Model
- CML Requirement Validation Result

Notes:

- This Practice is planned as a SimpleModeling with CML extension

---

## CML Modeling Practice

Purpose:

- Describe SimpleModeling models using CML
- Provide a structured source for model-driven development

Primary Alphas:

- Requirements
- Software System

Activity Spaces:

- Understand Requirements
- Shape the System

Activities:

- Write CML Model
- Validate CML Model
- Refine CML Model

Work Products:

- CML Model
- CML Validation Result

---

## Code Generation Practice

Purpose:

- Generate implementation artifacts from models
- Reduce repetitive coding work where model structure is stable

Primary Alpha:

- Software System

Activity Spaces:

- Implement the System

Activities:

- Configure Generator
- Generate Code
- Review Generated Code
- Regenerate Code

Work Products:

- Generator Configuration
- Generated Code
- Generation Log

---

# 6. Minimum Process Reference

## Minimum Activity Set

- Explore Context
- Identify Stakeholders and Actors
- Model Business Flow
- Find Actors and Use Cases
- Slice the Use Case
- Prepare a Use-Case Slice
- Make a BDD Specification
- Sprint Planning Solo
- Identify Subsystems
- Make Cloud Native CBD
- Evolve Cloud Native CBD
- Test a Use-Case Slice
- Make Documents
- Ensure CI/CD Pipeline to Test
- Sprint Review Solo
- Sprint Retrospective Solo

## Minimum Flow

1. Frame the opportunity and context
2. Identify stakeholders and actors
3. Model business flow
4. Find actors and use cases
5. Slice the use case
6. Prepare a use-case slice and BDD specification
7. Plan a solo sprint
8. Identify subsystems
9. Make Cloud Native CBD design
10. Evolve Cloud Native CBD implementation
11. Test the use-case slice
12. Update user documents
13. Maintain the CI/CD pipeline
14. Review the increment
15. Retrospect and improve the way of working

---

# 7. Composition Rules

- A Method always contains the Kernel
- A Method contains selected Practices
- A Method does not contain Practice Groups
- Practice Groups are peer-level and flat
- A Practice may appear in one or more Practice Groups depending on classification
- A Method should avoid selecting alternative Practices for the same responsibility
- Use Case Lite Practice and User Story Lite Practice are alternatives
- Scrum Solo Practice and Scrum Lite Practice are alternatives for solo development
- BDD with Use-Case Slice Lite Practice assumes Use Case Lite Practice
- CML Use-Case Modeling Practice is independent from Use Case Lite Practice

---

# 8. Working Notes

- This file is the temporary source of truth while the framework is being refined
- Practice definitions are intentionally lightweight
- Activity Spaces should be aligned with Essence terminology in a later pass
- Alphas should be checked against the official Essence Kernel in a later pass
- Work Products may later be split into formal Essence work products and informal artifacts
- Cloud Native CBD currently contains analysis, design, and implementation activities as sub-activities
- TDD is treated as a technique inside Cloud Native CBD and test work, not as an independent Practice in the reconstructed SimpleModeling method
- Essence Practice Group is kept as the reference-derived baseline
- Solo Development Practice Group is kept because AI-assisted development increases the practical importance of one-person development
- SimpleModeling Practice Group and SimpleModeling with CML Practice Group will be reconstructed from the lecture source material using current technology assumptions
