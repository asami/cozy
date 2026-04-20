# SimpleModeling with Cozy Development Process

status=draft

---

# 0. Purpose

This document defines the SimpleModeling with Cozy Development Process.

It is intended as a single-file working document for discussion with ChatGPT and for iterative refinement.

Development Process is used in the broad UP/RUP sense. It includes the Development Method, execution flow, work products, roles/agents, lifecycle structure, and automation support.

The document covers:

- Process definition
- Method view
- Practice Group classification
- Practice definitions
- Process flow references
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

## Development Process

Development Process
  = Method View
  + Process Flow View
  + Work Product View
  + Role and Agent View
  + Automation View
  + Lifecycle View

- Development Process is the broad development framework used for SimpleModeling work
- Development Process includes the Method View
- Development Process is not only an execution sequence

## Method View

Method
  = Kernel
  + selected Practices

- Kernel is fixed
- Kernel means Essence Kernel
- Practices are selected and composed into the Method View
- Practice Groups are not directly composed into the Method View

## Process Flow View

Process Flow View
  = ordered and iterative use of Activities and SKILLs

- Activities define abstract work units
- SKILLs define concrete executable or assistive procedures
- Process flows may vary by purpose while using the same Method View

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
- BDD with Use-Case Lite Practice

### Notes

- This group contains tool-independent SimpleModeling Practices
- Treat the lecture practices as source material, not as a fixed final structure

---

## SimpleModeling with Cozy Practice Group

- Cozy Environment Setup Practice
- BoK Practice
- CML Use-Case Modeling Practice
- Cozy Domain Modeling Practice
- Code Generation Practice
- CozyTextus Practice
- CI/CD Pipeline Practice
- User Environment Lite Practice
- DevOps Practice
- Security Practice

### Notes

- This group contains Practices based on the Cozy product family
- Practices in this group are Cozy-specific specializations of more general development concerns
- BoK Practice is placed in this group for now
- Reconstruct this group based on current CML, model-driven development, and Cozy product family technology
- Keep CML-based use-case modeling independent from Use Case Lite Practice

---

# 4. Method View

## SimpleModeling with Cozy Development Method View

SimpleModeling with Cozy Development Method View
  = Kernel
  + selected Practices

### Kernel

- Essence Kernel

### Selected Practices

- Use Case Lite Practice
- Scrum Solo Practice
- Business Modeling Solo Practice
- Cloud Native CBD Practice
- BDD with Use-Case Lite Practice
- Cozy Environment Setup Practice
- User Environment Lite Practice
- CI/CD Pipeline Practice
- DevOps Practice
- Security Practice

### Selection Notes

- This is the standard baseline Method View for Cozy-based SimpleModeling development
- Purpose-specific Development Process variants may be derived from this baseline
- One-person AI-assisted development is treated as the default operating mode
- This Method View selects Use Case Lite Practice instead of User Story Lite Practice
- Scrum Solo Practice is selected instead of Scrum Lite Practice
- Cloud Native CBD Practice provides analysis, design, and implementation activities
- CI/CD Pipeline Practice provides build, test, deploy, and delivery pipeline activities
- CML practices are not selected in the base method

---

# 5. Alphas

## Kernel Alphas

- Opportunity
- Stakeholders
- Requirements
- Software System
- Work
- Team
- Way of Working

## Practice-Specific Alphas

- Use Case
- Use-Case Slice
- User Story
- Sprint
- Daily
- Microservice

## SimpleModeling Working Alphas

- Business Context
- Business Flow
- Subsystem
- Component
- Domain Model
- Design Model
- BDD Specification
- TDD Specification
- User Environment
- CI/CD Pipeline
- DevOps Operation
- Security Finding
- Cozy Environment
- BoK
- Cozy Domain Model
- CozyTextus Model

### Notes

- Kernel Alphas are defined by the Essence Kernel
- Practice-Specific Alphas come from Essence or reconstructed Practices
- SimpleModeling Working Alphas are working candidates and may be normalized later
- Some items listed here may later become Work Products instead of Alphas

---

# 6. Practice Definitions

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

## BDD with Use-Case Lite Practice

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

## DevOps Practice

Source:

- `process-overview_fixed.pptx`
- `process-implementation_fixed.pptx`

Purpose:

- Provide DevOps management for Cozy-based development
- Keep operations, observability, and maintenance concerns visible
- Use AI assistance to make operational management nearly automatic for the developer

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
- Maintain Operational Checklist
- Feed Operational Findings into Backlog

Work Products:

- Operational Checklist
- Observability Note
- Improvement Backlog Item

Notes:

- Source material mentions GitHub, AWS, OpenTelemetry, Immutable Infrastructure, and 3R as implementation direction
- In Cozy-based development, DevOps is expected to be managed by the developer with AI assistance

---

## Security Practice

Source:

- `process-implementation_fixed.pptx`
- `process-text_fixed.pptx`

Purpose:

- Keep security concerns explicit during development and operation
- Provide lightweight security checks suitable for solo and AI-assisted development

Primary Alphas:

- Software System
- Way of Working

Activity Spaces:

- Implement the System
- Test the System
- Operate the System

Activities:

- Identify Security Concern
- Apply Security Check
- Review Security Finding
- Feed Security Finding into Backlog

Work Products:

- Security Checklist
- Security Finding
- Security Review Note

Notes:

- Security is separated from DevOps Practice to keep the responsibility explicit

---

## Cozy Environment Setup Practice

Purpose:

- Prepare the local and shared environments required for Cozy-based development
- Make Cozy product family tools, project templates, runtime settings, and verification commands ready for use

Primary Alphas:

- Software System
- Work
- Way of Working

Activity Spaces:

- Prepare to Do the Work
- Implement the System
- Test the System

Activities:

- Install Cozy Tools
- Configure Cozy Workspace
- Create Cozy Project Skeleton
- Verify Cozy Environment
- Document Environment Setup

Work Products:

- Cozy Workspace
- Cozy Project Skeleton
- Environment Configuration
- Setup Note
- Verification Result

Notes:

- This Practice should run before other SimpleModeling with Cozy Practices
- Environment setup covers development, generation, test, and local runtime prerequisites

---

## BoK Practice

Purpose:

- Maintain the Book of Knowledge used by SimpleModeling development
- Keep context, requirements, design rationale, operation notes, and feedback available to AI-assisted development

Primary Alphas:

- Opportunity
- Stakeholders
- Requirements
- Way of Working

Activity Spaces:

- Explore Possibilities
- Understand Stakeholder Needs
- Understand Requirements
- Support the Team

Activities:

- Capture Knowledge
- Organize Knowledge
- Link Knowledge to Work Products
- Update Knowledge from Feedback

Work Products:

- BoK Document
- Context Note
- Requirement Note
- Design Rationale
- Feedback Note

Notes:

- This Practice is placed under SimpleModeling with Cozy Practice Group for now
- BoK may later move to a Knowledge Processing Practice Group when that group is defined

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

- This Practice is planned as a SimpleModeling with Cozy extension

---

## Cozy Domain Modeling Practice

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

- Write Cozy Domain Model
- Validate Cozy Domain Model
- Refine Cozy Domain Model

Work Products:

- Cozy Domain Model
- Cozy Domain Model Validation Result

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

## CozyTextus Practice

Purpose:

- Implement SimpleModeling systems using Cozy and CozyTextus
- Connect Cozy domain models, generated artifacts, component runtime, tests, and deployment assets

Primary Alphas:

- Software System
- Work
- Way of Working

Activity Spaces:

- Shape the System
- Implement the System
- Test the System
- Deploy the System

Activities:

- Configure Cozy Project
- Generate CozyTextus Artifact
- Run Cozy Component
- Verify CozyTextus Integration

Work Products:

- Cozy Project
- CozyTextus Model
- Generated Component
- Runtime Configuration
- Verification Result

Notes:

- This Practice captures tool-supported SimpleModeling work

---

# 7. Process Flow View

Development Process is the broad framework. Process Flow View is the execution-flow view within that framework.

- Method View defines selected Practices
- Process Flow View defines the execution flow of Activities and SKILLs

Current flow reference:

- [SimpleModeling with Cozy Minimum Development Process](processes/simplemodeling-with-cozy-minimum-development-process.md)

---

# 8. Composition Rules

- A Method always contains the Kernel
- A Method contains selected Practices
- A Method does not contain Practice Groups
- Practice Groups are peer-level and flat
- A Practice may appear in one or more Practice Groups depending on classification
- A Method should avoid selecting alternative Practices for the same responsibility
- Use Case Lite Practice and User Story Lite Practice are alternatives
- Scrum Solo Practice and Scrum Lite Practice are alternatives for solo development
- BDD with Use-Case Lite Practice assumes Use Case Lite Practice
- SimpleModeling with Cozy Practices assume Cozy Environment Setup Practice
- CML Use-Case Modeling Practice is independent from Use Case Lite Practice
- BoK Practice is placed under SimpleModeling with Cozy Practice Group for now

---

# 9. Working Notes

- This file is the temporary source of truth while the framework is being refined
- Practice definitions are intentionally lightweight
- The working automation mapping is Practice -> Activity -> SKILL
- Activity is the abstract unit of work
- SKILL is the concrete executable or assistive procedure for an Activity
- `skills/<practice>/SKILL.md` represents a Practice overview or orchestration SKILL
- `skills/<practice>/<activity>/SKILL.md` represents an Activity-level SKILL
- Real operation combines manually performed Practices and Practices automated or assisted by SKILL files
- SKILL files support Practice execution; they do not replace Practice definitions
- Activity Spaces should be aligned with Essence terminology in a later pass
- Activity Spaces are loosely aligned with Essence Kernel Activity Spaces and may be normalized later
- Alphas should be checked against the official Essence Kernel in a later pass
- Work Products may later be split into formal Essence work products and informal artifacts
- Cloud Native CBD currently contains analysis, design, and implementation activities as sub-activities
- TDD is treated as a technique inside Cloud Native CBD and test work, not as an independent Practice in the reconstructed SimpleModeling method
- Essence Practice Group is kept as the reference-derived baseline
- Solo Development Practice Group is kept because AI-assisted development increases the practical importance of one-person development
- SimpleModeling Practice Group contains tool-independent SimpleModeling Practices
- SimpleModeling with Cozy Practice Group contains Cozy product family, CML, BoK, and tool-supported Practices
- BoK Practice may later move to a Knowledge Processing Practice Group
