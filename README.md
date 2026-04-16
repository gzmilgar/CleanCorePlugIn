# SAP RAP Code Generator - Eclipse Plugin

Eclipse ADT plugin that generates SAP ABAP RAP (RESTful Application Programming) applications from user inputs. Define your entities, fields, and service configuration through a guided UI, then generate all required ABAP RAP artifacts in one step.

## Features

- **CDS Field Fetching** - Fetch field definitions from existing CDS views via local cache or ADT connection
- **Complete RAP Artifact Generation** - Generates all 7 core artifacts:
  1. CDS Data Model (Interface View)
  2. CDS Projection View
  3. Metadata Extension
  4. Behavior Definition
  5. Behavior Implementation
  6. Service Definition
  7. Service Binding
- **Draft Support** - Optional draft enablement for generated RAP applications
- **Multi-Entity Support** - Define and manage multiple entities with parent-child relationships
- **Custom Actions** - Add custom actions with configurable placement and parameters
- **UI Annotation Configuration** - Configure UI annotations for list reports and object pages
- **ABAP Naming Validation** - Built-in validation for ABAP naming conventions
- **Release Contract Checking** - Check release status and successors for SAP objects
- **Code Formatting** - Generated code follows ABAP formatting best practices
- **Export Capability** - Export generated artifacts for use in your SAP system

## Installation

### Dropins Method

1. Build the plugin or obtain the plugin JAR file
2. Copy the JAR file to your Eclipse installation's `dropins/` folder:
   ```
   <eclipse-install-dir>/dropins/com.rap.generator_1.0.0.jar
   ```
3. Restart Eclipse
4. The **RAP Code Generator** view will be available under **Window > Show View > SAP RAP Generator**

### Requirements

- Eclipse IDE with ADT (ABAP Development Tools) installed
- Java 11 or later
- SAP system connection configured in ADT (for field fetching)

## Usage

1. Open the RAP Code Generator view from **Window > Show View > SAP RAP Generator**
2. **Configure Connection** - Set up your SAP system connection for CDS field fetching (optional)
3. **Define Entities** - Add entities and configure their fields, either manually or by fetching from existing CDS views
4. **Configure Actions** - Add custom actions to your entities as needed
5. **Set UI Annotations** - Configure list report and object page annotations
6. **Configure Service** - Set service definition and binding parameters
7. **Generate** - Click generate to produce all RAP artifacts
8. **Review & Export** - Review the generated code in the output panel and export to your project

## Screenshots

<!-- Add screenshots here -->

## Project Structure

```
com.rap.generator/
  META-INF/           - Plugin manifest
  src/                - Java source code
    com/rap/generator/
      model/          - Data models (RapApplication, EntityDefinition, etc.)
      generators/     - Code generators for each artifact type
      data/           - ADT connection and field fetching services
      ui/             - SWT-based user interface
      utils/          - Naming validation, type registry, utilities
  lib/                - Third-party libraries
  resources/          - Plugin resources
  plugin.xml          - Eclipse extension point declarations
```

## License

All rights reserved.
