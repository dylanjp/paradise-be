---
inclusion: fileMatch
fileMatchPattern: "be/src/main/resources/application*.properties"
---

# Spring Properties Conventions

## Use Kebab-Case for Property Keys

Always use kebab-case (lowercase with hyphens) for custom property keys in `application.properties`. Spring Boot's relaxed binding maps kebab-case keys to camelCase Java fields automatically.

```properties
# Good — kebab-case (canonical form)
drive.paths.my-drive=${DRIVE_PATH_MY_DRIVE}
drive.cache.media-cache=true

# Bad — camelCase causes "unknown property" warnings in the IDE
drive.paths.myDrive=${DRIVE_PATH_MY_DRIVE}
drive.cache.mediaCache=true
```

## Register Custom Properties in Metadata

Every custom property must be registered in `be/src/main/resources/META-INF/additional-spring-configuration-metadata.json` to avoid "unknown property" warnings.

- Properties from `@ConfigurationProperties` records: add entries to the metadata JSON
- Properties from `@Value` annotations: add entries to the metadata JSON

When adding a new `@ConfigurationProperties` record or a new `@Value` property, always add corresponding entries to the metadata file.

```json
{
  "properties": [
    {
      "name": "my.custom.property",
      "type": "java.lang.String",
      "description": "What this property does."
    }
  ]
}
```

## Configuration Processor Dependency

The project includes `spring-boot-configuration-processor` in `pom.xml`. This helps generate metadata for `@ConfigurationProperties` classes, but Java records may not always be picked up automatically. Always add manual entries to `additional-spring-configuration-metadata.json` as a safety net.
