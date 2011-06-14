package org.lilyproject.util.repo;

import org.lilyproject.repository.api.*;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * The idea behind SystemFields is to make system properties of records addressable as normal fields,
 * that is, to make them addressable by a QName. This way, both fields and system properties are
 * accessible through a uniform interface. This avoids the need to have two different addressing
 * systems in situations that want to provide access to both.
 *
 * <p>The namespace for all system fields is org.lilyproject.system</p>
 *
 * <p>At the time of this writing, SystemFields is used by the indexer and by conditional
 * record updates.</p>
 *
 * <p>It was not the intention to make the abstraction work the whole way, i.e. Record objects are not
 * decorated to make the system fields appear, nor are they reported as 'changed fields' in the record events, etc.
 * It seemed this could have all sorts of undesirable side-effects.

 * <p>For each of the supported system fields, corresponding 'fake' FieldType objects are available.
 * The UUIDs of these field type objects are name-based UUIDs, so they should never collide with those
 * generated by Lily.
 *
 * <p>The FieldType objects returned by this class are not clones, so be careful not to modify them.
 */
public class SystemFields {

    public static final String NS = "org.lilyproject.system";

    private static final SystemField[] fields = new SystemField[] {
            new SystemField("version", "LONG", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    // Special about the version field is that it can evaluate to null, while normal fields
                    // will never be null. Or should we rather throw a FieldNotFoundException when its null?
                    return record.getVersion();
                }
            },
            new SystemField("recordType", "STRING", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    return formatName(record.getRecordTypeName());
                }
            },
            new SystemField("recordTypeName", "STRING", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    return record.getRecordTypeName().getName();
                }
            },
            new SystemField("recordTypeNamespace", "STRING", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    return record.getRecordTypeName().getNamespace();
                }
            },
            new SystemField("recordTypeVersion", "LONG", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    return record.getRecordTypeVersion();
                }
            },
            new SystemField("recordTypeWithVersion", "STRING", false) {
                @Override
                public Object eval(Record record, TypeManager typeManager) {
                    return formatNameVersion(record.getRecordTypeName(), record.getRecordTypeVersion());
                }
            },
            new SystemField("mixins", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, false, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(formatName(recordType.getName()));
                        }
                    });
                    return result;
                }
            },
            new SystemField("mixinsWithVersion", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, false, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(formatNameVersion(recordType.getName(), recordType.getVersion()));
                        }
                    });
                    return result;
                }
            },
            new SystemField("mixinNames", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, false, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(recordType.getName().getName());
                        }
                    });
                    return result;
                }
            },
            new SystemField("mixinNamespaces", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, false, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(recordType.getName().getNamespace());
                        }
                    });
                    return result;
                }
            },
            new SystemField("recordTypes", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, true, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(formatName(recordType.getName()));
                        }
                    });
                    return result;
                }
            },
            new SystemField("recordTypesWithVersion", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, true, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(formatNameVersion(recordType.getName(), recordType.getVersion()));
                        }
                    });
                    return result;
                }
            },
            new SystemField("recordTypeNames", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, true, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(recordType.getName().getName());
                        }
                    });
                    return result;
                }
            },
            new SystemField("recordTypeNamespaces", "STRING", true) {
                @Override
                public Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                        InterruptedException {
                    final List<String> result = new NoDupsList<String>();
                    forEachMixin(record, typeManager, true, new MixinCallback() {
                        @Override
                        public void handle(RecordType recordType) {
                            result.add(recordType.getName().getNamespace());
                        }
                    });
                    return result;
                }
            },
    };

    private static SystemFields INSTANCE;

    private Map<QName, SystemField> fieldsByName;
    private Map<SchemaId, SystemField> fieldsById;

    public SystemFields(Map<QName, SystemField> fieldsByName, Map<SchemaId, SystemField> fieldsById) {
        this.fieldsByName = fieldsByName;
        this.fieldsById = fieldsById;
    }

    public static synchronized SystemFields getInstance(TypeManager typeManager, IdGenerator idGenerator) {

        if (INSTANCE == null) {

            Map<QName, SystemField> fieldsByName = new HashMap<QName, SystemField>();
            Map<SchemaId, SystemField> fieldsById = new HashMap<SchemaId, SystemField>();

            for (SystemField field : fields) {
                String stringId = "{" + NS + "}" + field.name;
                UUID id;
                try {
                    // Normally, the bytes should be prefixed with the UUID of the namespace to which the name
                    // belongs, but for our purpose this is a good enough.
                    id = UUID.nameUUIDFromBytes(stringId.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e); // rare enough
                }
                SchemaId schemaId = idGenerator.getSchemaId(id);

                ValueType valueType;
                try {
                    valueType = typeManager.getValueType(field.type, field.multiValue, false);
                } catch (RepositoryException e) {
                    throw new RuntimeException(e); // unlikely to occur
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e); // unlikely to occur
                }

                FieldType fieldType = typeManager.newFieldType(valueType, new QName(NS, field.name),
                        Scope.NON_VERSIONED);
                fieldType.setId(schemaId);

                field.setFieldType(fieldType);

                fieldsByName.put(fieldType.getName(), field);
                fieldsById.put(fieldType.getId(), field);
            }

            INSTANCE = new SystemFields(fieldsByName, fieldsById);
        }

        return INSTANCE;
    }

    public boolean isSystemField(QName name) {
        return fieldsByName.containsKey(name);
    }

    public boolean isSystemField(SchemaId schemaId) {
        return fieldsById.containsKey(schemaId);
    }

    public FieldType get(QName name) throws FieldTypeNotFoundException {
        if (!fieldsByName.containsKey(name)) {
            throw new FieldTypeNotFoundException(name);
        }

        return fieldsByName.get(name).fieldType;
    }

    public FieldType get(SchemaId schemaId) throws FieldTypeNotFoundException {
        if (!fieldsById.containsKey(schemaId)) {
            throw new FieldTypeNotFoundException(schemaId);
        }
        return fieldsByName.get(schemaId).fieldType;
    }

    public Object eval(Record record, FieldType fieldType, TypeManager typeManager)
            throws RepositoryException, InterruptedException {
        return fieldsById.get(fieldType.getId()).eval(record, typeManager);
    }

    /**
     * If it is a system field, evaluates it, if not, returns the normal field value from the record.
     * Does not throw a FieldNotFoundException, rather returns null.
     */
    public Object softEval(Record record, QName fieldType, TypeManager typeManager)
            throws RepositoryException, InterruptedException {
        if (isSystemField(fieldType)) {
            return fieldsByName.get(fieldType).eval(record, typeManager);
        } else {
            return record.getFields().get(fieldType);
        }
    }

    private static abstract class SystemField {
        private String name;
        private String type;
        private boolean multiValue;
        private FieldType fieldType;

        public SystemField(String name, String type, boolean multiValue) {
            this.name = name;
            this.type = type;
            this.multiValue = multiValue;
        }

        public void setFieldType(FieldType fieldType) {
            this.fieldType = fieldType;
        }

        public abstract Object eval(Record record, TypeManager typeManager) throws RepositoryException,
                InterruptedException;
    }

    private static String formatName(QName name) {
        return "{" + name.getNamespace() + "}" + name.getName();
    }

    private static String formatNameVersion(QName name, long version) {
        return "{" + name.getNamespace() + "}" + name.getName() + ":" + version;
    }

    private static interface MixinCallback {
        void handle(RecordType recordType);
    }

    private static void forEachMixin(Record record, TypeManager typeManager, boolean includeRecordType,
            MixinCallback callback) throws RepositoryException, InterruptedException {

        RecordType recordType = typeManager.getRecordTypeByName(record.getRecordTypeName(),
                record.getRecordTypeVersion());

        if (includeRecordType) {
            callback.handle(recordType);
        }

        for (Map.Entry<SchemaId, Long> mixin : recordType.getMixins().entrySet()) {
            RecordType mixinType = typeManager.getRecordTypeById(mixin.getKey(), mixin.getValue());
            callback.handle(mixinType);
        }
    }

    /**
     * Extends list to avoid that the same item is added twice (only for plain add). For the small lists we
     * have, assumed this would be cheaper than first constructing a set and then converting it to a list.
     */
    private static class NoDupsList<T> extends ArrayList<T> {
        @Override
        public boolean add(T t) {
            if (!this.contains(t)) {
                return super.add(t);
            }
            return false;
        }
    }
}