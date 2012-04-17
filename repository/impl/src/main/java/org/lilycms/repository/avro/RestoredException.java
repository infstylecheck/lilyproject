/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.repository.avro;

import org.lilycms.util.exception.RemoteThrowableInfo;

import java.util.List;

public class RestoredException extends Exception implements RemoteThrowableInfo {
    private String originalClass;
    private String originalMessage;
    private List<StackTraceElement> stackTrace;

    public RestoredException(String message, String originalClass, List<StackTraceElement> stackTrace) {
        super("[remote exception of type " + originalClass + "]" + message);
        this.originalMessage = message;
        this.originalClass = originalClass;
        this.stackTrace = stackTrace;
    }

    public String getOriginalClass() {
        return originalClass;
    }

    public List<StackTraceElement> getOriginalStackTrace() {
        return stackTrace;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }
}