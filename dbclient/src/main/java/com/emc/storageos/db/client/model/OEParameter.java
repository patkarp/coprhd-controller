/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.db.client.model;

/**
 * DB model class that represents a single parameter for a primitive
 */
@Cf("OEParameter")
public class OEParameter extends OEAbstractParameter {

    private String _value;
    private String _type;

    @Name("value")
    public String getValue() {
        return _value;
    };

    public void setValue(final String value) {
        _value = value;
        setChanged("value");
    };

    @Name("type")
    public String getType() {
        return _type;
    }

    public void setType(final String type) {
        _type = type;
        setChanged("type");
    }

    @Override
    public boolean isParameter() {
        return true;
    }

    @Override
    public OEParameter asParameter() {
        return this;
    }

    @Override
    public boolean isParameterList() {
        return false;
    }

    @Override
    public OEParameterList asParameterList() {
        return null;
    }
}
