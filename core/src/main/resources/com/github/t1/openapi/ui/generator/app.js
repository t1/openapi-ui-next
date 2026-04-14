document.addEventListener('DOMContentLoaded', function() {
    const STORAGE_PREFIX_GLOBAL_HEADER = 'openapi-ui-global-header:';
    const STORAGE_PREFIX_PARAM = 'openapi-ui-param:';
    const STORAGE_PREFIX_CUSTOM_HEADER = 'openapi-ui-custom-header:';
    const STORAGE_KEY_VIEW = 'openapi-ui-view';

    const detail = document.getElementById('detail');
    const fieldCache = new Map();
    const responseCache = new Map();
    const schemaToggleCache = new Map();

    // Generator registry: each key is a display label, each value is a generator function
    const generators = {
        curl: function(params) {
            const headerFlags = Object.keys(params.headers).filter(function(h) {
                return h !== 'Cookie';
            }).map(function(h) {
                return "-H '" + h + ": " + params.headers[h] + "'";
            }).join(' ');
            const cookieParts = [];
            Object.keys(params.headers).forEach(function(h) {
                if (h === 'Cookie') {
                    const cookieValue = params.headers[h];
                    cookieParts.push(cookieValue);
                }
            });
            let cmd = 'curl -X ' + params.method;
            if (headerFlags) cmd += ' ' + headerFlags;
            if (cookieParts.length > 0) cmd += " -b '" + cookieParts.join('; ') + "'";
            if (params.body) cmd += " -H 'Content-Type: " + (params.contentType || 'application/json') + "' -d '" + params.body + "'";
            cmd += ' ' + params.url;
            return cmd;
        },
        httpie: function(params) {
            const headerArgs = Object.keys(params.headers).filter(function(h) {
                return h !== 'Cookie';
            }).map(function(h) {
                return h + ':' + params.headers[h];
            }).join(' ');
            const cookieParts = [];
            Object.keys(params.headers).forEach(function(h) {
                if (h === 'Cookie') {
                    cookieParts.push(params.headers[h]);
                }
            });
            
            // Apply httpie short form: scheme becomes the command, GET is the default method,
            // localhost is replaced by :<port> when a non-default port is present
            const urlObj = new URL(params.url);
            const isHttps = urlObj.protocol === 'https:';
            const command = isHttps ? 'https' : 'http';
            const isLocalhost = urlObj.hostname === 'localhost';
            const pathAndQuery = urlObj.pathname + urlObj.search;
            let host;
            if (isLocalhost) {
                host = urlObj.port ? ':' + urlObj.port : 'localhost';
            } else {
                host = urlObj.hostname + (urlObj.port ? ':' + urlObj.port : '');
            }
            const hostAndPath = host + pathAndQuery;
            const methodPart = params.method === 'GET' ? '' : params.method + ' ';
            
            let cmd = params.body
                    ? "echo '" + params.body + "' | " + command + ' ' + methodPart + hostAndPath + " Content-Type:" + (params.contentType || 'application/json')
                    : command + ' ' + methodPart + hostAndPath;
            if (headerArgs) cmd += ' ' + headerArgs;
            if (cookieParts.length > 0) cmd += ' Cookie:' + cookieParts.join('\\; ');
            return cmd;
        },
        'JS fetch': function(params) {
            const headersObj = {};
            Object.keys(params.headers).forEach(function(h) {
                headersObj[h] = params.headers[h];
            });
            if (params.body && params.contentType) {
                headersObj['Content-Type'] = params.contentType;
            }
            
            const headersStr = JSON.stringify(headersObj, null, 4);
            let code = "const response = await fetch('" + params.url + "', {\n" +
                       "    method: '" + params.method + "'";
            if (Object.keys(headersObj).length > 0) {
                code += ",\n    headers: " + headersStr;
            }
            if (params.body) {
                code += ",\n    body: JSON.stringify(" + params.body + ")";
            }
            code += "\n});\nconst data = await response.json();";
            return code;
        },
        'Java HttpClient': function(params) {
            let code = "HttpRequest request = HttpRequest.newBuilder()\n" +
                       "    .uri(URI.create(\"" + params.url + "\"))";
            
            Object.keys(params.headers).forEach(function(h) {
                code += "\n    .header(\"" + h + "\", \"" + params.headers[h] + "\")";
            });
            
            if (params.body) {
                code += "\n    .header(\"Content-Type\", \"" + (params.contentType || 'application/json') + "\")";
                code += "\n    ." + params.method + "(HttpRequest.BodyPublishers.ofString(\"" + 
                        params.body.replace(/"/g, '\\"') + "\"))";
            } else {
                code += "\n    ." + params.method + "(HttpRequest.BodyPublishers.noBody())";
            }
            
            code += "\n    .build();\n" +
                    "HttpResponse<String> response = HttpClient.newHttpClient()\n" +
                    "    .send(request, HttpResponse.BodyHandlers.ofString());";
            return code;
        },
        'JAX-RS': function(params) {
            const pathOnly = params.url.replace(/^https?:\/\/[^/]+/, '');
            const baseUrl = params.url.substring(0, params.url.length - pathOnly.length);
            
            let code = "Response response = ClientBuilder.newClient()\n" +
                       "    .target(\"" + baseUrl + "\").path(\"" + pathOnly + "\")\n" +
                       "    .request(MediaType.APPLICATION_JSON)";
            
            Object.keys(params.headers).forEach(function(h) {
                code += "\n    .header(\"" + h + "\", \"" + params.headers[h] + "\")";
            });
            
            if (params.body) {
                code += "\n    ." + params.method.toLowerCase() + "(Entity.json(" + params.body + "));";
            } else {
                code += "\n    ." + params.method.toLowerCase() + "(Entity.json(null));";
            }
            
            return code;
        },
        'Python': function(params) {
            let code = "import requests\n";
            
            const headersObj = {};
            Object.keys(params.headers).forEach(function(h) {
                headersObj[h] = params.headers[h];
            });
            
            const headersStr = Object.keys(headersObj).length > 0 
                ? JSON.stringify(headersObj) 
                : '{}';
            
            code += "response = requests." + params.method.toLowerCase() + "(";
            code += "\"" + params.url + "\"";
            
            if (Object.keys(headersObj).length > 0) {
                code += ", headers=" + headersStr;
            }
            
            if (params.body) {
                code += ", json=" + params.body;
            }
            
            code += ")";
            
            return code;
        },
        'Go': function(params) {
            let code = "";
            
            if (params.body) {
                code += "jsonBody := `" + params.body + "`\n";
                code += "body := strings.NewReader(jsonBody)\n";
                code += "req, _ := http.NewRequest(\"" + params.method + "\", \"" + params.url + "\", body)\n";
            } else {
                code += "req, _ := http.NewRequest(\"" + params.method + "\", \"" + params.url + "\", nil)\n";
            }
            
            Object.keys(params.headers).forEach(function(h) {
                code += "req.Header.Set(\"" + h + "\", \"" + params.headers[h] + "\")\n";
            });
            
            if (params.body) {
                code += "req.Header.Set(\"Content-Type\", \"" + (params.contentType || 'application/json') + "\")\n";
            }
            
            code += "resp, _ := http.DefaultClient.Do(req)";
            
            return code;
        },
        'MP Rest Client': function(params) {
            // Extract path parts for interface name and method path
            const urlObj = new URL(params.url);
            const pathParts = urlObj.pathname.split('/').filter(function(p) { return p; });
            const lastPart = pathParts.length > 0 ? pathParts[pathParts.length - 1] : 'Resource';
            const resourceName = lastPart.charAt(0).toUpperCase() + lastPart.slice(1);
            const interfaceName = resourceName + 'Client';
            
            // Generate record DTOs from schema if available
            let requestRecord = '';
            let responseRecord = '';
            
            if (params.schema && params.schema.requestBody && params.schema.requestBody.properties) {
                const reqProps = params.schema.requestBody.properties;
                const reqFields = Object.keys(reqProps).map(function(key) {
                    const prop = reqProps[key];
                    const javaType = prop.type === 'integer' ? 'int' : 
                                   prop.type === 'number' ? 'double' :
                                   prop.type === 'boolean' ? 'boolean' : 'String';
                    return javaType + ' ' + key;
                }).join(', ');
                if (reqFields) {
                    requestRecord = '\nrecord ' + resourceName + 'Request(' + reqFields + ') {}';
                }
            }
            
            if (params.schema && params.schema.responses && params.schema.responses['200'] && params.schema.responses['200'].properties) {
                const respProps = params.schema.responses['200'].properties;
                const respFields = Object.keys(respProps).map(function(key) {
                    const prop = respProps[key];
                    const javaType = prop.type === 'integer' ? 'long' : 
                                   prop.type === 'number' ? 'double' :
                                   prop.type === 'boolean' ? 'boolean' : 'String';
                    return javaType + ' ' + key;
                }).join(', ');
                if (respFields) {
                    responseRecord = '\nrecord ' + resourceName + 'Response(' + respFields + ') {}';
                }
            }
            
            const baseUrl = urlObj.origin;
            const path = urlObj.pathname;
            
            let code = '@RegisterRestClient(baseUri = "' + baseUrl + '")\n';
            code += 'public interface ' + interfaceName + ' {\n';
            code += '    @' + params.method + ' @Path("' + path + '")\n';
            code += '    @Consumes(MediaType.APPLICATION_JSON)\n';
            
            const returnType = responseRecord ? resourceName + 'Response' : 'String';
            const paramType = requestRecord ? resourceName + 'Request' : 'String';
            const methodName = params.method.toLowerCase() + resourceName;
            
            code += '    ' + returnType + ' ' + methodName + '(' + paramType + ' body);\n';
            code += '}';
            
            if (requestRecord) code += requestRecord;
            if (responseRecord) code += responseRecord;
            
            return code;
        },
        'Spring WebClient': function(params) {
            const urlObj = new URL(params.url);
            const baseUrl = urlObj.origin;
            const path = urlObj.pathname + urlObj.search;
            
            let code = 'String result = WebClient.create("' + baseUrl + '")\n';
            code += '    .' + params.method.toLowerCase() + '().uri("' + path + '")';
            
            Object.keys(params.headers).forEach(function(h) {
                code += '\n    .header("' + h + '", "' + params.headers[h] + '")';
            });
            
            if (params.body) {
                code += '\n    .contentType(MediaType.APPLICATION_JSON)';
                code += '\n    .bodyValue(' + params.body + ')';
            }
            
            code += '\n    .retrieve().bodyToMono(String.class).block();';
            
            return code;
        },
        'Spring RestTemplate': function(params) {
            let code = 'HttpHeaders headers = new HttpHeaders();\n';
            
            Object.keys(params.headers).forEach(function(h) {
                code += 'headers.set("' + h + '", "' + params.headers[h] + '");\n';
            });
            
            if (params.body) {
                code += 'headers.setContentType(MediaType.APPLICATION_JSON);\n';
                code += 'HttpEntity<String> entity = new HttpEntity<>(' + params.body + ', headers);\n';
            } else {
                code += 'HttpEntity<String> entity = new HttpEntity<>(headers);\n';
            }
            
            code += 'ResponseEntity<String> response = new RestTemplate()\n';
            code += '    .exchange("' + params.url + '", HttpMethod.' + params.method + ', entity, String.class);';
            
            return code;
        }
    };

    function autoGrow(textarea) {
        textarea.style.height = 'auto';
        textarea.style.height = textarea.scrollHeight + 'px';
    }

    function opKey(form) {
        return form.getAttribute('data-method') + ':' + form.getAttribute('data-path');
    }

    function updateBaseUrl(url) {
        const modeToggle = document.querySelector('[data-toggle="mode"]');
        if (modeToggle) {
            modeToggle.setAttribute('data-base-url', url);
        }
        updateAuthFieldVisibility(url);
    }

    function updateAuthFieldVisibility(serverUrl) {
        if (!serverUrl) return;
        
        try {
            const serverOrigin = new URL(serverUrl, window.location.href).origin;
            const currentOrigin = window.location.origin;
            const isCrossOrigin = serverOrigin !== currentOrigin;
            
            // Toggle visibility of browser-handled auth fields
            const browserHandledFields = document.querySelectorAll('[data-browser-handled="true"]');
            browserHandledFields.forEach(function(field) {
                if (isCrossOrigin) {
                    field.style.display = '';
                } else {
                    field.style.display = 'none';
                }
            });
        } catch (e) {
            // Invalid URL - ignore
        }
    }

    function saveFields(form) {
        const key = opKey(form);
        const fields = {};
        form.querySelectorAll('input[name], select[name], textarea[data-request-body]').forEach(function(el) {
            const name = el.getAttribute('name') || '__body__';
            fields[name] = el.type === 'checkbox' ? el.checked : el.value;
        });
        fieldCache.set(key, fields);
    }

    function restoreFields(form) {
        const key = opKey(form);
        const fields = fieldCache.get(key);
        if (!fields) return;
        form.querySelectorAll('input[name], select[name], textarea[data-request-body]').forEach(function(el) {
            const name = el.getAttribute('name') || '__body__';
            if (!(name in fields)) return;
            if (el.type === 'checkbox') el.checked = fields[name];
            else el.value = fields[name];
        });
    }

    function findNestedContent(propName) {
        var sibling = propName;
        while (sibling && !(sibling.nextElementSibling && sibling.nextElementSibling.classList.contains('schema-nested'))) {
            sibling = sibling.nextElementSibling;
        }
        return sibling ? sibling.nextElementSibling : null;
    }

    function saveSchemaToggles(form) {
        const key = opKey(form);
        const state = {};
        form.querySelectorAll('.schema-box[data-box]').forEach(function(box) {
            state[box.getAttribute('data-box')] = !box.classList.contains('is-collapsed');
        });
        var nested = [];
        form.querySelectorAll('.schema-nested-toggle[aria-expanded="true"]').forEach(function(toggle) {
            var propName = toggle.closest('.schema-prop-name');
            if (propName) nested.push(propName.getAttribute('data-prop'));
        });
        state._nested = nested;
        schemaToggleCache.set(key, state);
    }

    function restoreSchemaToggles(form) {
        const key = opKey(form);
        const state = schemaToggleCache.get(key);
        if (!state) return;
        form.querySelectorAll('.schema-box[data-box]').forEach(function(box) {
            const boxType = box.getAttribute('data-box');
            if (boxType in state) {
                const expanded = state[boxType];
                box.classList.toggle('is-collapsed', !expanded);
                const toggle = box.querySelector('.schema-toggle');
                if (toggle) toggle.textContent = expanded ? 'Schema ▼' : 'Schema ▶';
            }
        });
        if (state._nested) {
            state._nested.forEach(function(prop) {
                var propName = form.querySelector('.schema-prop-name[data-prop="' + prop + '"]');
                if (!propName) return;
                var toggle = propName.querySelector('.schema-nested-toggle');
                if (toggle) toggle.setAttribute('aria-expanded', 'true');
                var nested = findNestedContent(propName);
                if (nested) nested.classList.add('is-expanded');
            });
        }
    }

    function restoreResponse(form) {
        const key = opKey(form);
        const resp = responseCache.get(key);
        if (!resp) return;
        const area = form.querySelector('.response-area');
        if (!area) return;
        showResponse(area, form, resp.status, resp.statusText, resp.headers, resp.body, resp.ct, resp.headersExpanded);
    }

    const shortcutMod = /Mac/.test(navigator.platform) ? 'Ctrl' : 'Alt';

    // Mode toggle consumer
    const modeContainer = document.querySelector('[data-toggle="mode"]');
    if (modeContainer) {
        // Set platform-appropriate keyboard shortcut tooltips
        const modeButtons = modeContainer.querySelectorAll('[data-toggle-value]');
        modeButtons.forEach(function(btn, i) {
            const title = btn.getAttribute('title') || '';
            btn.setAttribute('title', title + ' (' + shortcutMod + '+' + (i + 1) + ')');
        });
        const modeSendButton = modeContainer.querySelector('.mode-send-button');
        modeContainer.addEventListener('toggle', function(e) {
            modeContainer.setAttribute('data-mode', e.detail.value);
            const isTry = e.detail.value === 'try';
            if (modeSendButton) modeSendButton.textContent = isTry ? 'Send' : 'Copy';
            detail.querySelectorAll('[data-param-in="cookie"]').forEach(function(inp) { inp.disabled = isTry; });
        });
        if (modeSendButton) {
            modeSendButton.addEventListener('click', function(e) {
                e.stopPropagation();
                const form = detail.querySelector('form[data-path]');
                if (form) form.requestSubmit();
            });
        }
        modeContainer.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (modeSendButton) modeSendButton.click();
            } else if (e.key === 'Tab' && e.shiftKey) {
                // Shift+Tab: if previous focusable is a status tab, jump to the active one
                var allFocusable = Array.from(document.querySelectorAll(
                    'input, select, textarea, button, a[tabindex="0"], [tabindex="0"]'
                )).filter(function(f) { return f.offsetParent !== null && !f.disabled; });
                var idx = allFocusable.indexOf(modeContainer);
                for (var pi = idx - 1; pi >= 0; pi--) {
                    var prev = allFocusable[pi];
                    if (prev.closest && prev.closest('.toggle')) continue;
                    if (prev.classList.contains('schema-status-tab')) {
                        var activeTab = prev.closest('.schema-status-tabs').querySelector('.schema-status-tab.is-active');
                        if (activeTab) { e.preventDefault(); activeTab.focus(); return; }
                    }
                    break;
                }
            }
            // ArrowUp/ArrowDown are handled by spatial navigation
        });
        document.addEventListener('keydown', function(e) {
            const mod = /Mac/.test(navigator.platform) ? e.ctrlKey : e.altKey;
            const digit = mod && e.code >= 'Digit1' && e.code <= 'Digit4' ? parseInt(e.code.charAt(5)) : 0;
            if (digit) {
                e.preventDefault();
                if (digit === 4) {
                    const dropdownMenu = document.querySelector('.mode-dropdown-menu');
                    if (dropdownMenu) dropdownMenu.classList.toggle('is-active');
                } else {
                    const values = Array.from(modeContainer.querySelectorAll('[data-toggle-value]:not([data-overflow])')).map(function(el) {
                        return el.getAttribute('data-toggle-value');
                    });
                    modeContainer._select(values[digit - 1]);
                }
            }
        });

        // Dropdown menu toggle and recently used tracking
        const overflowBtn = modeContainer.querySelector('[data-overflow]');
        const dropdownMenu = document.querySelector('.mode-dropdown-menu');
        if (overflowBtn && dropdownMenu) {
            overflowBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                dropdownMenu.classList.toggle('is-active');
            });
            document.addEventListener('click', function() {
                dropdownMenu.classList.remove('is-active');
            });
            // Dropdown item selection
            dropdownMenu.querySelectorAll('.mode-dropdown-item').forEach(function(item) {
                item.addEventListener('click', function() {
                    const generator = item.getAttribute('data-generator');
                    updateRecentFormats(generator);
                    modeContainer.setAttribute('data-mode', generator);
                    modeContainer._select(generator);
                    dropdownMenu.classList.remove('is-active');
                    // Update send button text
                    if (modeSendButton) modeSendButton.textContent = 'Copy';
                });
            });
        }
        
        // Track recently used formats (excluding 'try' which is always first)
        function updateRecentFormats(format) {
            if (format === 'try') return;
            var recent = JSON.parse(localStorage.getItem('openapi-ui-recent-formats') || '[]');
            recent = recent.filter(function(f) { return f !== format; });
            recent.unshift(format);
            if (recent.length > 2) recent = recent.slice(0, 2);
            localStorage.setItem('openapi-ui-recent-formats', JSON.stringify(recent));
        }
    }

    function createHeaderRow(name, value, persisted) {
        const row = document.createElement('div');
        row.className = 'field custom-header-row';
        if (persisted && name) row.setAttribute('data-prev-name', name);
        const escapedName = name ? name.replace(/"/g, '&quot;') : '';
        const escapedValue = value ? value.replace(/"/g, '&quot;') : '';
        const nameAttr = name ? '' : ' name="' + randomName() + '"';
        const valueAttr = name ? '' : ' name="' + randomName() + '"';
        var placeholder = 'Header name';
        var nameSize = ' size="' + Math.max(name ? name.length : placeholder.length, 1) + '"';
        row.innerHTML =
            '<label class="label custom-header-label">' +
            '<input type="text" class="custom-header-name"' + nameAttr + nameSize + ' placeholder="Header name"' + (name ? ' value="' + escapedName + '"' : '') + '>' +
            '<div class="tags has-addons is-inline-flex ml-2"><span class="tag">header</span><span class="tag is-info">custom</span><a class="tag is-delete custom-header-remove" tabindex="0"></a></div>' +
            '</label>' +
            '<div class="control has-icons-right">' +
            '<input type="text" class="input is-small custom-header-value"' + valueAttr + ' placeholder="Value"' + (value ? ' value="' + escapedValue + '"' : '') + '>' +
            '<span class="icon is-small is-right persist-toggle" aria-pressed="' + (persisted ? 'true' : 'false') + '" title="Pin value (' + shortcutMod + '+P)"><i class="fa-solid fa-thumbtack"></i></span>' +
            '</div>';
        return row;
    }

    function createCustomUrlRow(url) {
        const row = document.createElement('label');
        row.className = 'custom-url-row server-radio-label';
        const escapedUrl = url ? url.replace(/"/g, '&quot;') : '';
        const radioId = 'custom-url-' + randomName();
        row.setAttribute('for', radioId);
        row.innerHTML =
            '<input type="radio" name="server" id="' + radioId + '" value="' + escapedUrl + '">' +
            '<input type="text" class="custom-url-input" placeholder="Server URL" value="' + escapedUrl + '">' +
            '<button type="button" class="delete is-small"></button>';
        return row;
    }

    function createTemplatePresetForm(serverIndex, urlTemplate, variables) {
        const form = document.createElement('div');
        form.className = 'template-preset-form';
        form.setAttribute('data-server-index', serverIndex);
        form.setAttribute('data-url-template', urlTemplate);
        
        let html = '<div class="template-preset-form-fields">';
        variables.forEach(function(variable) {
            html += '<div class="field">';
            html += '<label class="label is-small">' + variable.name + '</label>';
            html += '<div class="control">';
            
            if (variable.enum && variable.enum.length > 0) {
                // Dropdown for enum-constrained variables
                html += '<div class="select is-small is-fullwidth">';
                html += '<select name="' + variable.name + '">';
                variable.enum.forEach(function(value) {
                    const selected = (value === variable.default) ? ' selected' : '';
                    html += '<option value="' + value + '"' + selected + '>' + value + '</option>';
                });
                html += '</select>';
                html += '</div>';
            } else {
                // Text input for free-form variables
                html += '<input type="text" class="input is-small" name="' + variable.name + '" value="' + (variable.default || '') + '" placeholder="' + variable.name + '">';
            }
            
            html += '</div>';
            if (variable.description) {
                html += '<p class="help">' + variable.description + '</p>';
            }
            html += '</div>';
        });
        html += '</div>';
        html += '<div class="buttons">';
        html += '<button type="button" class="button is-small is-primary template-preset-save">Save</button>';
        html += '<button type="button" class="button is-small template-preset-cancel">Cancel</button>';
        html += '</div>';
        
        form.innerHTML = html;
        form.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                form.querySelector('.template-preset-save').click();
            } else if (e.key === 'Escape') {
                e.preventDefault();
                e.stopPropagation();
                form.querySelector('.template-preset-cancel').click();
            }
        });
        return form;
    }

    function saveCustomUrls() {
        const serverSelector = document.getElementById('server-selector');
        if (!serverSelector) return;
        const rows = serverSelector.querySelectorAll('.custom-url-row');
        const urls = Array.from(rows).map(function(row) { return row.querySelector('.custom-url-input').value; }).filter(function(url) { return url; });
        localStorage.setItem('openapi-ui-custom-urls', JSON.stringify(urls));
    }

    function templatePresetsStorageKey(urlTemplate) {
        return 'openapi-ui-template-presets:' + encodeURIComponent(urlTemplate);
    }

    function createTemplatePresetLabel(serverIndex, presetIndex, resolvedUrl, isDeletable) {
        const presetId = 'server-' + serverIndex + '-preset-' + presetIndex;
        const label = document.createElement('label');
        label.className = 'template-preset-label';
        label.setAttribute('for', presetId);
        const deleteBtn = isDeletable ? '<button type="button" class="delete is-small"></button>' : '';
        label.innerHTML =
            '<input type="radio" name="server" id="' + presetId + '" value="' + resolvedUrl + '">' +
            '<span class="server-item-url">' + resolvedUrl + '</span>' +
            deleteBtn;
        return label;
    }

    function restoreCustomUrls() {
        const serverSelector = document.getElementById('server-selector');
        if (!serverSelector) return;
        const stored = localStorage.getItem('openapi-ui-custom-urls');
        if (!stored) return;
        try {
            const urls = JSON.parse(stored);
            const addBtn = serverSelector.querySelector('.custom-url-add');
            if (!addBtn) return;
            const customUrlBlock = addBtn.closest('.custom-url-block');
            urls.forEach(function(url) {
                const row = createCustomUrlRow(url);
                customUrlBlock.insertBefore(row, addBtn);
            });
        } catch (e) {
            console.error('Failed to restore custom URLs:', e);
        }
    }

    function restoreTemplatePresets() {
        const serverSelector = document.getElementById('server-selector');
        if (!serverSelector) return;
        
        // Find all "Add preset" buttons (one per template server)
        const addPresetButtons = serverSelector.querySelectorAll('.template-preset-add');
        addPresetButtons.forEach(function(addBtn) {
            const serverIndex = addBtn.getAttribute('data-server-index');
            const urlTemplate = addBtn.getAttribute('data-url-template');
            const storageKey = templatePresetsStorageKey(urlTemplate);
            const stored = localStorage.getItem(storageKey);
            if (!stored) return;
            
            try {
                const presets = JSON.parse(stored);
                const templateGroup = addBtn.closest('.template-group');
                
                // Count existing presets to calculate next index
                const existingPresets = serverSelector.querySelectorAll("input[id^='server-" + serverIndex + "-preset-']");
                let nextIndex = existingPresets.length;
                
                presets.forEach(function(preset) {
                    const label = createTemplatePresetLabel(serverIndex, nextIndex, preset.resolvedUrl, true);
                    
                    // Insert before the "Add preset" button
                    templateGroup.insertBefore(label, addBtn);
                    nextIndex++;
                });
            } catch (e) {
                console.error('Failed to restore template presets for ' + urlTemplate + ':', e);
            }
        });
    }

    // Server selector dropdown toggle
    const serverSelector = document.getElementById('server-selector');
    if (serverSelector) {
        const serverTrigger = serverSelector.querySelector('.dropdown-trigger button');
        function focusCheckedServerRadio() {
            var checked = serverSelector.querySelector('input[type="radio"][name="server"]:checked');
            if (checked) checked.focus();
        }
        if (serverTrigger) {
            serverTrigger.addEventListener('click', function(e) {
                e.stopPropagation();
                if (!serverSelector.classList.contains('is-active')) {
                    focusBeforeDropdown = document.activeElement;
                }
                serverSelector.classList.toggle('is-active');
                if (serverSelector.classList.contains('is-active')) focusCheckedServerRadio();
            });
        }
        // Tooltip with keyboard shortcut
        serverTrigger.setAttribute('title', 'Server (' + shortcutMod + '+0)');

        // Select server radio on focus (spatial navigation focuses them)
        serverSelector.addEventListener('focusin', function(e) {
            var radio = e.target.closest('input[type="radio"][name="server"]');
            if (radio && !radio.checked) {
                radio.checked = true;
                radio.dispatchEvent(new Event('change', {bubbles: true}));
            }
        });

        // Keyboard navigation inside server dropdown
        var focusBeforeDropdown = null;
        serverSelector.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                serverSelector.classList.remove('is-active');
                if (focusBeforeDropdown) focusBeforeDropdown.focus();
                else serverTrigger.focus();
                e.preventDefault();
                return;
            }
            if (e.key === 'Enter' && (e.target.matches('input[type="radio"]') || e.target.matches('.custom-url-input'))) {
                serverSelector.classList.remove('is-active');
                if (focusBeforeDropdown) focusBeforeDropdown.focus();
                else serverTrigger.focus();
                e.preventDefault();
                return;
            }
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                var direction = e.key === 'ArrowDown' ? 'down' : 'up';
                var target = findSpatialTarget(e.target, direction);
                if (target) target.focus();
                else bump(e.target, 'v');
                e.preventDefault();
            }
        });
        // Ctrl+0 (Mac) / Alt+0 (other) to toggle server dropdown
        document.addEventListener('keydown', function(e) {
            var mod = /Mac/.test(navigator.platform) ? e.ctrlKey : e.altKey;
            if (mod && e.code === 'Digit0') {
                e.preventDefault();
                if (!serverSelector.classList.contains('is-active')) {
                    focusBeforeDropdown = document.activeElement;
                    serverSelector.classList.add('is-active');
                    focusCheckedServerRadio();
                } else {
                    serverSelector.classList.remove('is-active');
                    if (focusBeforeDropdown) focusBeforeDropdown.focus();
                    else serverTrigger.focus();
                }
            }
        });

        // Close dropdown when clicking outside — track in capture phase
        // whether click started inside the selector, because DOM removal can
        // detach the target before the bubble phase reaches the document
        var serverClickInside = false;
        serverSelector.addEventListener('click', function() {
            serverClickInside = true;
        }, true);
        document.addEventListener('click', function() {
            if (!serverClickInside) {
                serverSelector.classList.remove('is-active');
            }
            serverClickInside = false;
        });

        // Handle server radio selection (both non-template and template servers)
        serverSelector.addEventListener('change', function(e) {
            if (e.target.matches('input[type="radio"][name="server"]')) {
                var url = e.target.value;
                serverSelector.querySelector('.server-dropdown-trigger .server-url').textContent = url;
                updateBaseUrl(url);
                localStorage.setItem('openapi-ui-server', url);
            }
        });

        // Live-update trigger button while typing in a selected custom URL field
        serverSelector.addEventListener('input', function(e) {
            var input = e.target.closest('.custom-url-input');
            if (!input) return;
            var row = input.closest('.custom-url-row');
            var radio = row.querySelector('input[type="radio"]');
            if (radio && radio.checked) {
                var url = input.value;
                radio.value = url;
                var triggerUrl = serverSelector.querySelector('.server-dropdown-trigger .server-url');
                if (triggerUrl) triggerUrl.textContent = url;
                updateBaseUrl(url);
            }
        });

        // Handle dropdown item clicks (custom URLs, template presets, etc.)
        serverSelector.addEventListener('click', function(e) {
            const addBtn = e.target.closest('.custom-url-add');
            if (addBtn) {
                const customUrlBlock = addBtn.closest('.custom-url-block');
                const row = createCustomUrlRow('');
                customUrlBlock.insertBefore(row, addBtn);
                var radio = row.querySelector('input[type="radio"]');
                if (radio) {
                    radio.checked = true;
                    radio.dispatchEvent(new Event('change', { bubbles: true }));
                }
                row.querySelector('.custom-url-input').focus();
                return;
            }
            const removeBtn = e.target.closest('.custom-url-row .delete');
            if (removeBtn) {
                const row = removeBtn.closest('.custom-url-row');
                var deletedRadio = row.querySelector('input[type="radio"]');
                
                // Find the radio above (or below if first) before removing
                var allRadios = Array.from(serverSelector.querySelectorAll('input[name="server"]'));
                var deletedIndex = allRadios.indexOf(deletedRadio);
                var fallbackIndex = deletedIndex > 0 ? deletedIndex - 1 : 1;
                var fallbackRadio = allRadios[fallbackIndex] || allRadios[0];
                
                row.remove();
                saveCustomUrls();
                
                // Focus fallback if the deleted row had the selected radio
                if (fallbackRadio && !serverSelector.querySelector('input[name="server"]:checked')) {
                    fallbackRadio.focus();
                }
                return;
            }
            const addPresetBtn = e.target.closest('.template-preset-add');
            if (addPresetBtn) {
                const serverIndex = addPresetBtn.getAttribute('data-server-index');
                const urlTemplate = addPresetBtn.getAttribute('data-url-template');
                const variables = JSON.parse(addPresetBtn.getAttribute('data-variables'));
                const form = createTemplatePresetForm(serverIndex, urlTemplate, variables);
                const templateGroup = addPresetBtn.closest('.template-group');
                templateGroup.insertBefore(form, addPresetBtn);
                var firstField = form.querySelector('input, select');
                if (firstField) firstField.focus();
                return;
            }
            const saveBtn = e.target.closest('.template-preset-save');
            if (saveBtn) {
                const form = saveBtn.closest('.template-preset-form');
                const serverIndex = form.getAttribute('data-server-index');
                const urlTemplate = form.getAttribute('data-url-template');
                
                // Read form values and resolve template
                const inputs = form.querySelectorAll('input, select');
                const variables = {};
                let resolvedUrl = urlTemplate;
                inputs.forEach(function(input) {
                    const varName = input.getAttribute('name');
                    const varValue = input.value;
                    variables[varName] = varValue;
                    resolvedUrl = resolvedUrl.replace('{' + varName + '}', varValue);
                });
                
                // Count existing presets for this server to determine the new preset index
                const existingPresets = serverSelector.querySelectorAll("input[id^='server-" + serverIndex + "-preset-']");
                const newPresetIndex = existingPresets.length;
                
                // Create new preset radio (user-created presets are deletable)
                const label = createTemplatePresetLabel(serverIndex, newPresetIndex, resolvedUrl, true);
                
                // Insert new preset before the form
                form.parentNode.insertBefore(label, form);
                
                // Focus the newly created preset (focusin handler selects it)
                var newRadio = label.querySelector('input[type="radio"]');
                newRadio.focus();
                
                // Save preset to localStorage
                const storageKey = templatePresetsStorageKey(urlTemplate);
                const stored = localStorage.getItem(storageKey);
                const presets = stored ? JSON.parse(stored) : [];
                presets.push({variables: variables, resolvedUrl: resolvedUrl});
                localStorage.setItem(storageKey, JSON.stringify(presets));
                
                // Remove the form
                form.remove();
                return;
            }
            const cancelBtn = e.target.closest('.template-preset-cancel');
            if (cancelBtn) {
                const form = cancelBtn.closest('.template-preset-form');
                const templateGroup = form.closest('.template-group');
                const checkedRadio = templateGroup.querySelector('input[name="server"]:checked')
                    || templateGroup.querySelector('input[name="server"]');
                form.remove();
                if (checkedRadio) checkedRadio.focus();
                return;
            }
            const deleteBtn = e.target.closest('.template-preset-label .delete');
            if (deleteBtn) {
                const label = deleteBtn.closest('.template-preset-label');
                const radio = label.querySelector('input[type="radio"]');
                const resolvedUrl = radio.value;
                
                // Find the template server this preset belongs to
                const templateGroup = label.closest('.template-group');
                const addPresetBtn = templateGroup.querySelector('.template-preset-add');
                const urlTemplate = addPresetBtn.getAttribute('data-url-template');
                
                // Find the radio above (or below if first) before removing
                var allRadios = Array.from(serverSelector.querySelectorAll('input[name="server"]'));
                var deletedIndex = allRadios.indexOf(radio);
                var fallbackIndex = deletedIndex > 0 ? deletedIndex - 1 : 1;
                var fallbackRadio = allRadios[fallbackIndex] || allRadios[0];
                
                // Remove from DOM
                label.remove();
                
                // Focus fallback (focusin handler selects it)
                if (fallbackRadio && !serverSelector.querySelector('input[name="server"]:checked')) {
                    fallbackRadio.focus();
                }
                
                // Remove from localStorage
                const storageKey = templatePresetsStorageKey(urlTemplate);
                const stored = localStorage.getItem(storageKey);
                if (stored) {
                    try {
                        let presets = JSON.parse(stored);
                        presets = presets.filter(function(p) { return p.resolvedUrl !== resolvedUrl; });
                        if (presets.length > 0) {
                            localStorage.setItem(storageKey, JSON.stringify(presets));
                        } else {
                            localStorage.removeItem(storageKey);
                        }
                    } catch (e) {
                        console.error('Failed to update template presets in localStorage:', e);
                    }
                }
                return;
            }
        });

        // Handle custom URL input changes
        serverSelector.addEventListener('input', function(e) {
            if (e.target.classList.contains('custom-url-input')) {
                const url = e.target.value;
                const row = e.target.closest('.custom-url-row');
                const radio = row.querySelector('input[type="radio"]');
                radio.value = url;
                saveCustomUrls();
            }
        });

        // Note: server radio selection is handled by the unified 'change' listener above

        // Restore custom URLs from localStorage
        restoreCustomUrls();

        // Restore template presets from localStorage
        restoreTemplatePresets();

        // Resolve origin for no-servers case
        var triggerUrlEl = serverSelector.querySelector('.server-dropdown-trigger .server-url');
        if (triggerUrlEl && !triggerUrlEl.textContent.trim()) {
            triggerUrlEl.textContent = window.location.origin;
            var originRadio = serverSelector.querySelector('input[type="radio"][name="server"][value=""]');
            if (originRadio) {
                originRadio.value = window.location.origin;
                var originLabel = originRadio.closest('.server-row');
                if (originLabel) {
                    var urlDiv = originLabel.querySelector('.server-item-url');
                    if (urlDiv) urlDiv.textContent = window.location.origin;
                }
            }
        }

        // Restore selected server from localStorage
        const savedServer = localStorage.getItem('openapi-ui-server');
        if (savedServer) {
            var radios = serverSelector.querySelectorAll('input[type="radio"][name="server"]');
            for (var ri = 0; ri < radios.length; ri++) {
                if (radios[ri].value === savedServer) {
                    radios[ri].checked = true;
                    triggerUrlEl.textContent = savedServer;
                    updateBaseUrl(savedServer);
                    break;
                }
            }
        }
    }

    // Global headers panel toggle
    const globalHeadersPanel = document.getElementById('global-headers');
    if (globalHeadersPanel) {
        globalHeadersPanel.querySelector('.global-headers-toggle').addEventListener('click', function() {
            globalHeadersPanel.classList.toggle('is-collapsed');
        });
        globalHeadersPanel.addEventListener('click', function(e) {
            const addBtn = e.target.closest('.custom-header-add');
            if (addBtn) {
                const row = addGlobalHeaderRow('', '', false);
                row.querySelector('.custom-header-name').focus();
                updateGlobalHeaderCount();
                applyGlobalHeaderPlaceholders();
                return;
            }
            const removeBtn = e.target.closest('.custom-header-remove');
            if (removeBtn) {
                const row = removeBtn.closest('.custom-header-row');
                const persistBtn = row.querySelector('.persist-toggle');
                if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                    const name = row.querySelector('.custom-header-name').value.trim();
                    if (name) localStorage.removeItem(STORAGE_PREFIX_GLOBAL_HEADER + name);
                }
                row.remove();
                updateGlobalHeaderCount();
                applyGlobalHeaderPlaceholders();
                return;
            }
        });
    }

    function addGlobalHeaderRow(name, value, persisted) {
        var addBlock = globalHeadersPanel.querySelector('.global-headers-add');
        var row = createHeaderRow(name, value, persisted);
        row.classList.add('panel-block');
        globalHeadersPanel.insertBefore(row, addBlock);
        return row;
    }

    function updateGlobalHeaderCount() {
        if (!globalHeadersPanel) return;
        const count = globalHeadersPanel.querySelectorAll('.custom-header-row').length;
        globalHeadersPanel.querySelector('.global-headers-count').textContent = count;
    }

    function toggleHeaderPersistence(persistBtn, storageKeyFn) {
        const pressed = persistBtn.getAttribute('aria-pressed') === 'true';
        persistBtn.setAttribute('aria-pressed', String(!pressed));
        const row = persistBtn.closest('.custom-header-row');
        const name = row.querySelector('.custom-header-name').value.trim();
        const value = row.querySelector('.custom-header-value').value;
        if (!pressed && name) {
            localStorage.setItem(storageKeyFn(name), value);
            row.setAttribute('data-prev-name', name);
        } else if (name) {
            localStorage.removeItem(storageKeyFn(name));
            row.removeAttribute('data-prev-name');
        }
    }

    function syncPersistedHeader(row, storageKeyFn) {
        const persistBtn = row.querySelector('.persist-toggle');
        if (!persistBtn || persistBtn.getAttribute('aria-pressed') !== 'true') return;
        const name = row.querySelector('.custom-header-name').value.trim();
        const value = row.querySelector('.custom-header-value').value;
        const prevName = row.getAttribute('data-prev-name');
        if (prevName && prevName !== name) localStorage.removeItem(storageKeyFn(prevName));
        if (name) {
            localStorage.setItem(storageKeyFn(name), value);
            row.setAttribute('data-prev-name', name);
        }
    }

    function globalHeaderKey(name) { return STORAGE_PREFIX_GLOBAL_HEADER + name; }

    // Global header persistence
    if (globalHeadersPanel) {
        globalHeadersPanel.addEventListener('click', function(e) {
            const persistBtn = e.target.closest('.persist-toggle');
            if (!persistBtn) return;
            toggleHeaderPersistence(persistBtn, globalHeaderKey);
        });
        globalHeadersPanel.addEventListener('input', function(e) {
            const inp = e.target.closest('.custom-header-name, .custom-header-value');
            if (!inp) return;
            if (inp.classList.contains('custom-header-name')) {
                inp.size = Math.max(inp.value.length, inp.placeholder.length, 1);
            }
            applyGlobalHeaderPlaceholders();
            syncPersistedHeader(inp.closest('.custom-header-row'), globalHeaderKey);
        });
        // Restore persisted global headers
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (!key.startsWith(STORAGE_PREFIX_GLOBAL_HEADER)) continue;
            const ghName = key.substring(STORAGE_PREFIX_GLOBAL_HEADER.length);
            const ghValue = localStorage.getItem(key);
            addGlobalHeaderRow(ghName, ghValue, true);
        }
        updateGlobalHeaderCount();
        applyGlobalHeaderPlaceholders();
    }

    function applyGlobalHeaderPlaceholders() {
        const globals = {};
        if (globalHeadersPanel) {
            globalHeadersPanel.querySelectorAll('.custom-header-row').forEach(function(row) {
                const name = row.querySelector('.custom-header-name').value.trim();
                const value = row.querySelector('.custom-header-value').value;
                if (name) globals[name.toLowerCase()] = value;
            });
        }
        document.querySelectorAll('[data-param-in="header"]').forEach(function(el) {
            const name = el.getAttribute('name');
            if (!name) return;
            if (!el.hasAttribute('data-original-placeholder')) {
                el.setAttribute('data-original-placeholder', el.getAttribute('placeholder') || '');
            }
            const globalValue = globals[name.toLowerCase()];
            if (globalValue) {
                el.setAttribute('placeholder', globalValue + ' \u00A0\u00A0\u00A0// from global headers');
            } else {
                const original = el.getAttribute('data-original-placeholder');
                if (original) el.setAttribute('placeholder', original);
                else el.removeAttribute('placeholder');
            }
        });
    }

    // View toggle consumer
    const viewToggle = document.querySelector('[data-toggle="view"]');
    if (viewToggle) {
        viewToggle.addEventListener('toggle', function(e) {
            const btn = viewToggle.querySelector('[data-toggle-value=' + e.detail.value + ']');
            htmx.ajax('GET', btn.getAttribute('hx-get'), {target: '#tree-container', swap: 'innerHTML'}).then(function() {
                initFilterIcon();
                viewToggle.focus();
            });
        });
        viewToggle.addEventListener('keydown', function(e) {
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                const modeToggle = document.querySelector('[data-toggle="mode"]');
                if (modeToggle) modeToggle.focus();
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                const tree = document.querySelector('[role="tree"]');
                if (tree) tree.focus();
            } else if (e.key === 'Tab') {
                e.preventDefault();
                if (e.shiftKey) {
                    const modeToggle = document.querySelector('[data-toggle="mode"]');
                    if (modeToggle) modeToggle.focus();
                } else {
                    const filterIcon = document.querySelector('.filter-icon');
                    if (filterIcon) {
                        filterIcon.focus();
                    } else {
                        const tree = document.querySelector('[role="tree"]');
                        if (tree) tree.focus();
                    }
                }
            }
        });
    }

    // Override persisted view when URL hash specifies a view
    if (decodeURIComponent(location.hash).match(/^#\[/)) localStorage.setItem(STORAGE_KEY_VIEW, 'tags');

    // Restore persisted toggle state (after consumers registered)
    document.querySelectorAll('.toggle[data-persist]').forEach(function(container) {
        const saved = localStorage.getItem(container.getAttribute('data-persist'));
        if (saved && saved !== container.querySelector('.is-active').getAttribute('data-toggle-value')) {
            container._select(saved);
        }
    });

    // Tab switching — toggle is-active when HTMX swaps method content
    document.body.addEventListener('htmx:afterRequest', function(e) {
        const tabLink = e.detail.elt;
        if (tabLink && tabLink.closest && tabLink.closest('.tabs')) {
            const tabs = tabLink.closest('.tabs');
            tabs.querySelectorAll('li').forEach(function(li) { li.classList.remove('is-active'); });
            tabLink.closest('li').classList.add('is-active');
            const hxGet = tabLink.getAttribute('hx-get');
            if (hxGet) history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
        }
    });

    function initDescriptionToggle() {
        document.querySelectorAll('.op-description-wrapper').forEach(function(wrapper) {
            const desc = wrapper.querySelector('.op-description');
            const toggle = wrapper.querySelector('.desc-toggle');
            if (!desc || !toggle) return;
            if (desc.scrollHeight > desc.clientHeight) {
                wrapper.classList.add('is-clamped');
            } else {
                wrapper.classList.remove('is-clamped');
            }
            toggle.onclick = function() {
                const expanded = wrapper.classList.toggle('is-expanded');
                wrapper.classList.toggle('is-clamped', !expanded);
                toggle.querySelector('span').textContent = expanded ? '▼' : '▶';
                toggle.setAttribute('aria-label', expanded ? 'Collapse description' : 'Expand description');
            };
        });
    }

    document.body.addEventListener('htmx:afterSettle', function(e) {
        if (e.detail.target && e.detail.target.id === 'tree-container') {
            initFilterIcon();
            if (window._hashNavPending) {
                window._hashNavPending = false;
                navigateFromHash();
            } else if (viewToggle) {
                viewToggle.focus();
            }
        }
    });
    document.body.addEventListener('htmx:load', function(e) {
        if (!pendingMethod || !detail || !detail.contains(e.target)) return;
        const method = pendingMethod;
        pendingMethod = null;
        const tabLinks = detail.querySelectorAll('.tabs li a');
        var tabClicked = false;
        tabLinks.forEach(function(a) {
            if (a.textContent.trim() === method) { a.click(); tabClicked = true; }
        });
        // If no tab was clicked (single-method path), apply pending focus now
        if (!tabClicked) {
            if (window._pendingRestoreFocus) {
                var selector = window._pendingRestoreFocus;
                window._pendingRestoreFocus = null;
                var el = document.querySelector('#detail ' + selector);
                if (el) el.focus({preventScroll: true});
            } else if (window._pendingFocusFirst) {
                window._pendingFocusFirst = false;
                var targetForm = document.querySelector('#detail form[data-path]');
                if (targetForm) {
                    var firstInput = targetForm.querySelector('input, select, textarea');
                    if (firstInput) firstInput.focus();
                    else if (modeContainer) modeContainer.focus();
                }
            }
        }
        // Restore scroll position after back navigation
        if (window._pendingScrollY != null) {
            var scrollY = window._pendingScrollY;
            window._pendingScrollY = null;
            requestAnimationFrame(function() { window.scrollTo(0, scrollY); });
        }
    });
    // Move mode selector into the form, before the response area
    var detailPane = document.querySelector('.detail-pane');
    function moveModeSelector() {
        var modeSel = document.querySelector('.mode-selector-container');
        var responseArea = detail.querySelector('.response-area');
        if (modeSel && responseArea) {
            var modeRow = document.querySelector('.mode-row');
            if (!modeRow) {
                modeRow = document.createElement('div');
                modeRow.className = 'mode-row';
            }
            responseArea.parentNode.insertBefore(modeRow, responseArea);
            modeRow.appendChild(modeSel);
        }
    }
    // Rescue mode selector back to detail-pane before HTMX replaces content inside detail
    document.body.addEventListener('htmx:beforeSwap', function(e) {
        if (!e.detail.target || !detail.contains(e.detail.target)) return;
        var modeSel = document.querySelector('.mode-selector-container');
        if (modeSel && detailPane) detailPane.appendChild(modeSel);
        // Clean up mode-row and any moved response-info
        var modeRow = document.querySelector('.mode-row');
        if (modeRow) modeRow.remove();
    });
    document.body.addEventListener('htmx:afterSwap', function(e) {
        moveModeSelector();
        const currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
        const isTry = currentMode === 'try';
        detail.querySelectorAll('[data-param-in="cookie"]').forEach(function(inp) { inp.disabled = isTry; });
        // Update auth field visibility based on current server
        const modeToggle = document.querySelector('[data-toggle="mode"]');
        if (modeToggle) {
            updateAuthFieldVisibility(modeToggle.getAttribute('data-base-url'));
        }
        initDescriptionToggle();
        // Initialize persist toggle icons on server-rendered param fields
        detail.querySelectorAll('.field .icon.is-right').forEach(function(icon) {
            icon.classList.add('persist-toggle');
            icon.setAttribute('aria-pressed', 'false');
            icon.setAttribute('title', 'Pin value (' + shortcutMod + '+P)');
        });
        // Restore persisted spec-defined parameter values
        document.querySelectorAll('[data-param-in]').forEach(function(el) {
            const form = el.closest('form[data-path]');
            if (!form) return;
            const inp = paramControl(el);
            const key = paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name'));
            const saved = localStorage.getItem(key);
            if (saved !== null) {
                if (inp.type === 'checkbox') inp.checked = saved === 'true';
                else inp.value = saved;
                const persistBtn = inp.closest('.field').querySelector('.persist-toggle');
                if (persistBtn) persistBtn.setAttribute('aria-pressed', 'true');
            }
        });
        // Restore persisted per-op custom headers
        let swapForm = document.querySelector('#detail form[data-path]');
        if (swapForm) {
            const prefix = customHeaderStorageKey(swapForm, '');
            const container = swapForm.querySelector('.custom-headers');
            if (container) {
                const addBtn = container.querySelector('.custom-header-add');
                for (let si = 0; si < localStorage.length; si++) {
                    const sKey = localStorage.key(si);
                    if (!sKey.startsWith(prefix)) continue;
                    const chName = sKey.substring(prefix.length);
                    const chValue = localStorage.getItem(sKey);
                    const chRow = createHeaderRow(chName, chValue, true);
                    container.insertBefore(chRow, addBtn);
                }
            }
        }
        applyGlobalHeaderPlaceholders();
        document.querySelectorAll('select[data-example-select]').forEach(function(sel) {
            sel.addEventListener('change', function() {
                const textarea = sel.closest('.field').querySelector('textarea[data-request-body]');
                if (textarea) {
                    textarea.value = sel.value;
                    autoGrow(textarea);
                }
            });
        });
        // Restore session-cached field values and response
        const restoreForm = document.querySelector('#detail form[data-path]');
        if (restoreForm) {
            const area = restoreForm.querySelector('.response-area');
            if (area) initialResponseArea = area.innerHTML;
            restoreFields(restoreForm);
            restoreResponse(restoreForm);
            restoreSchemaToggles(restoreForm);
        }
        // Auto-grow request body textareas
        document.querySelectorAll('textarea[data-request-body]').forEach(function(ta) {
            ta.addEventListener('input', function() { autoGrow(ta); });
            new ResizeObserver(function() { autoGrow(ta); }).observe(ta);
            autoGrow(ta);
        });
        // Fill parameters from hash query string or body link navigation.
        // Wait until method tab selection is done (pendingMethod is null) so params go into the right form.
        if (window._pendingParams && !pendingMethod) {
            var params = window._pendingParams;
            window._pendingParams = null;
            var targetForm = document.querySelector('#detail form[data-path]');
            var lastFilled = null;
            if (targetForm) {
                for (var name in params) {
                    var input = targetForm.querySelector('[name="' + name + '"]');
                    if (input) {
                        input.value = params[name];
                        input.dispatchEvent(new Event('input', {bubbles: true}));
                        lastFilled = input;
                    }
                }
            }
            if (lastFilled) lastFilled.focus();
        } else if (window._pendingRestoreFocus && !pendingMethod) {
            var selector = window._pendingRestoreFocus;
            window._pendingRestoreFocus = null;
            var el = document.querySelector('#detail ' + selector);
            if (el) el.focus({preventScroll: true});
        } else if (window._pendingFocusFirst && !pendingMethod) {
            window._pendingFocusFirst = false;
            var targetForm = document.querySelector('#detail form[data-path]');
            if (targetForm) {
                var firstInput = targetForm.querySelector('input, select, textarea');
                if (firstInput) firstInput.focus();
                else if (modeContainer) modeContainer.focus();
            }
        }
    });

    function prettyPrintXml(xml) {
        const doc = new DOMParser().parseFromString(xml, 'application/xml');
        if (doc.querySelector('parsererror')) return xml;
        const xslt = new DOMParser().parseFromString(
            '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">' +
            '<xsl:output method="xml" indent="yes"/>' +
            '<xsl:template match="@*|node()"><xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy></xsl:template>' +
            '</xsl:stylesheet>', 'application/xml');
        const processor = new XSLTProcessor();
        processor.importStylesheet(xslt);
        const result = processor.transformToDocument(doc);
        return new XMLSerializer().serializeToString(result);
    }

    function randomName() {return 'h-' + Math.random().toString(36).slice(2);}

    function paramStorageKey(form, paramIn, name) {
        if (paramIn === 'path') {
            const pathInputs = Array.from(form.querySelectorAll('[data-param-in="path"]'));
            let paramIndex = 0;
            for (let p = 0; p < pathInputs.length; p++) {
                if (pathInputs[p].getAttribute('name') === name) { paramIndex = p; break; }
            }
            const pathTemplate = form.getAttribute('data-fragment-path');
            const segments = pathTemplate.split('/');
            const positionPath = [];
            let paramCount = 0;
            for (let i = 0; i < segments.length; i++) {
                const isParam = segments[i].charAt(0) === '{';
                if (isParam) {
                    positionPath.push('{}');
                    if (paramCount === paramIndex) break;
                    paramCount++;
                } else {
                    positionPath.push(segments[i]);
                }
            }
            return STORAGE_PREFIX_PARAM + 'path:' + positionPath.join('/');
        }
        return STORAGE_PREFIX_PARAM + paramIn + ':' + form.getAttribute('data-method') + ':' + form.getAttribute('data-path') + ':' + name;
    }

    function customHeaderStorageKey(form, name) {
        return STORAGE_PREFIX_CUSTOM_HEADER + form.getAttribute('data-method') + ':' + form.getAttribute('data-path') + ':' + name;
    }

    function paramControl(el) {
        const inner = el.querySelector('select');
        return inner || el;
    }

    function paramValue(inp) {
        return inp.type === 'checkbox' ? String(inp.checked) : inp.value;
    }

    function detectLanguage(contentType) {
        const mime = (contentType || '').split(';')[0].trim();
        const subtype = mime.split('/')[1] || '';
        const suffix = subtype.includes('+') ? subtype.split('+').pop() : subtype;
        if (typeof hljs !== 'undefined' && hljs.getLanguage(suffix)) return suffix;
        return null;
    }

    // Spec-defined param persistence
    detail.addEventListener('click', function(e) {
        const persistBtn = e.target.closest('#detail .field .persist-toggle');
        if (!persistBtn) return;
        const fieldEl = persistBtn.closest('.field');
        const el = fieldEl.querySelector('[data-param-in]');
        if (!el) return; // not a spec-defined param (e.g. custom header row)
        const pressed = persistBtn.getAttribute('aria-pressed') === 'true';
        persistBtn.setAttribute('aria-pressed', String(!pressed));
        const inp = paramControl(el);
        const form = el.closest('form[data-path]');
        const key = paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name'));
        if (!pressed) {
            localStorage.setItem(key, paramValue(inp));
        } else {
            localStorage.removeItem(key);
        }
    });
    detail.addEventListener('change', function(e) {
        // Persisted param value change (checkbox or select)
        const paramEl = e.target.closest('[data-param-in]');
        if (paramEl) {
            const fieldEl = paramEl.closest('.field');
            const persistBtn = fieldEl.querySelector('.persist-toggle');
            if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                const inp = paramControl(paramEl);
                const form = paramEl.closest('form[data-path]');
                const key = paramStorageKey(form, paramEl.getAttribute('data-param-in'), inp.getAttribute('name'));
                localStorage.setItem(key, paramValue(inp));
            }
            return;
        }
        // Per-op custom header persistence — handled via click on persist-toggle
    });
    // Per-op custom header persist toggle
    detail.addEventListener('click', function(e) {
        const persistBtn = e.target.closest('.custom-headers .persist-toggle');
        if (!persistBtn) return;
        const form = persistBtn.closest('form[data-path]');
        toggleHeaderPersistence(persistBtn, function(n) { return customHeaderStorageKey(form, n); });
    });
    detail.addEventListener('input', function(e) {
        const el = e.target.closest('[data-param-in]');
        if (el) {
            const fieldEl = el.closest('.field');
            const persistBtn = fieldEl.querySelector('.persist-toggle');
            if (!persistBtn || persistBtn.getAttribute('aria-pressed') !== 'true') return;
            const inp = paramControl(el);
            const form = el.closest('form[data-path]');
            localStorage.setItem(paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name')), paramValue(inp));
            return;
        }
        // Per-op custom header input update
        const customInp = e.target.closest('.custom-headers .custom-header-name, .custom-headers .custom-header-value');
        if (customInp) {
            if (customInp.classList.contains('custom-header-name')) {
                customInp.size = Math.max(customInp.value.length, customInp.placeholder.length, 1);
            }
            const row = customInp.closest('.custom-header-row');
            const form = row.closest('form[data-path]');
            syncPersistedHeader(row, function(n) { return customHeaderStorageKey(form, n); });
        }
    });

    // Save field values to session cache on every input
    detail.addEventListener('input', function(e) {
        const form = e.target.closest('form[data-path]');
        if (form) saveFields(form);
    });

    // Schema box toggle + custom header management
    detail.addEventListener('click', function(e) {
        const addBtn = e.target.closest('.custom-header-add');
        if (addBtn) {
            const container = addBtn.closest('.custom-headers');
            const row = createHeaderRow('', '', false);
            container.insertBefore(row, addBtn);
            row.querySelector('.custom-header-name').focus();
            return;
        }
        const removeBtn = e.target.closest('.custom-header-remove');
        if (removeBtn) {
            const row = removeBtn.closest('.custom-header-row');
            const persistBtn = row.querySelector('.persist-toggle');
            if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                const form = row.closest('form[data-path]');
                const name = row.querySelector('.custom-header-name').value.trim();
                if (form && name) localStorage.removeItem(customHeaderStorageKey(form, name));
            }
            row.remove();
            return;
        }
        const toggle = e.target.closest('.schema-toggle');
        if (toggle) {
            const box = toggle.closest('.schema-box');
            box.classList.toggle('is-collapsed');
            toggle.textContent = box.classList.contains('is-collapsed') ? 'Schema ▶' : 'Schema ▼';
            toggle.focus(); // Safari doesn't focus buttons on click
            const form = toggle.closest('form[data-path]');
            if (form) saveSchemaToggles(form);
            return;
        }
        const nestedToggle = e.target.closest('.schema-nested-toggle');
        if (nestedToggle) {
            const expanded = nestedToggle.getAttribute('aria-expanded') === 'true';
            nestedToggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
            nestedToggle.focus(); // Safari doesn't focus buttons on click
            const propName = nestedToggle.closest('.schema-prop-name');
            var nested = findNestedContent(propName);
            if (nested) nested.classList.toggle('is-expanded');
            const form = nestedToggle.closest('form[data-path]');
            if (form) saveSchemaToggles(form);
            return;
        }
        const tab = e.target.closest('.schema-status-tab');
        if (tab) activateStatusTab(tab);
    });

    function activateStatusTab(tab) {
        const tabs = tab.closest('.schema-status-tabs');
        tabs.querySelectorAll('.schema-status-tab').forEach(function(t) { t.classList.remove('is-active'); });
        tab.classList.add('is-active');
        tab.focus();
        const box = tab.closest('.schema-box');
        box.querySelectorAll('.schema-status-panel').forEach(function(p) { p.style.display = 'none'; });
        const panel = box.querySelector('.schema-status-panel[data-status="' + tab.textContent + '"]');
        if (panel) panel.style.display = '';
    }

    const fragmentCache = new Map();
    let initialResponseArea = null;

    function fragmentBaseUrl(form) {
        const path = form.getAttribute('data-fragment-path');
        const method = form.getAttribute('data-method');
        return path + '/' + method + '-response-';
    }

    function clearPreviousResponse() {
        const area = detail.querySelector('.response-area');
        if (!area) return;
        if (!initialResponseArea) initialResponseArea = area.innerHTML;
        area.innerHTML = initialResponseArea;
    }

    function showResponse(area, form, status, statusText, headers, body, contentType, headersExpanded) {
        const base = fragmentBaseUrl(form);
        const specificUrl = base + status + '.html';
        const fallbackUrl = base + 'fallback.html';
        let isFallback = false;

        function populate(html) {
            area.innerHTML = html;
            const badge = area.querySelector('.response-status');
            if (isFallback && badge) {
                badge.textContent = status === 0 ? statusText : status + ' ' + statusText;
                if (status >= 200 && status < 300) badge.classList.add('is-success');
                else badge.classList.add('is-error');
            }
            // populate documented header values
            if (headers && headers.length > 0) {
                const headersByName = {};
                headers.forEach(function(h) { headersByName[h.name.toLowerCase()] = h.value; });
                area.querySelectorAll('.response-header-value[data-header]').forEach(function(el) {
                    const name = el.getAttribute('data-header');
                    if (headersByName[name] !== undefined) {
                        el.textContent = headersByName[name];
                    } else if (el.hasAttribute('data-required')) {
                        el.textContent = '(missing)';
                        el.classList.add('response-header-missing');
                        const nameEl = el.previousElementSibling;
                        if (nameEl) nameEl.classList.add('response-header-missing');
                    } else {
                        if (name === 'set-cookie') {
                            el.textContent = 'browser sends these automatically, not visible to JS';
                        } else {
                            el.textContent = '\u2014';
                        }
                        el.classList.add('response-header-absent');
                    }
                });
                // append undocumented headers
                const docGrid = area.querySelector('.response-documented-headers');
                let undocGrid = area.querySelector('.response-headers-undocumented');
                if (!undocGrid) {
                    undocGrid = document.createElement('div');
                    undocGrid.className = 'response-header-rows response-headers-undocumented';
                    const headersContainer = area.querySelector('.response-headers');
                    if (headersContainer) headersContainer.appendChild(undocGrid);
                }
                const documentedNames = {};
                area.querySelectorAll('.response-documented-headers .response-header-name').forEach(function(el) {
                    documentedNames[el.textContent.toLowerCase()] = true;
                });
                const hasDocumented = Object.keys(documentedNames).length > 0;
                const undocumented = [];
                headers.forEach(function(h) {
                    if (hasDocumented && documentedNames[h.name.toLowerCase()]) return;
                    if (!hasDocumented) {
                        // no documented headers in fragment — show all in main grid
                        const grid = docGrid || undocGrid;
                        appendHeaderRow(grid, h.name, h.value);
                    } else {
                        undocumented.push(h);
                    }
                });
                undocumented.forEach(function(h) {
                    appendHeaderRow(undocGrid, h.name, h.value);
                });
                // show-all button
                if (hasDocumented && undocumented.length > 0) {
                    undocGrid.style.display = 'none';
                    const showAllBtn = document.createElement('button');
                    showAllBtn.type = 'button';
                    showAllBtn.className = 'response-headers-show-all';
                    showAllBtn.textContent = 'Show all (' + undocumented.length + ' more)';
                    const headersContainer = area.querySelector('.response-headers');
                    headersContainer.insertBefore(showAllBtn, undocGrid);
                }
                // auto-detect: expand if any documented header has an actual value
                if (headersExpanded === null) {
                    let hasMatch = false;
                    area.querySelectorAll('.response-documented-headers .response-header-value[data-header]').forEach(function(el) {
                        if (el.textContent && el.textContent !== '(missing)' && el.textContent !== '\u2014') hasMatch = true;
                    });
                    headersExpanded = hasMatch;
                }
                // update toggle text with count
                const toggle = area.querySelector('.response-headers-toggle');
                if (toggle) {
                    toggle.textContent = 'Headers (' + headers.length + ') ' + (headersExpanded ? '▼' : '▶');
                }
                // expand headers if requested
                const headersContainer = area.querySelector('.response-headers');
                if (headersContainer && headersExpanded) headersContainer.classList.add('is-expanded');
            } else {
                const headersBox = area.querySelector('.response-headers');
                if (headersBox) {
                    const toggle = headersBox.querySelector('.response-headers-toggle');
                    if (toggle) toggle.style.display = 'none';
                    const emptyMsg = headersBox.querySelector('.response-empty');
                    if (emptyMsg) emptyMsg.style.display = '';
                }
            }
            // populate body
            const pre = area.querySelector('pre.response');
            if (body && body.trim()) {
                if (pre) {
                    const lang = detectLanguage(contentType);
                    if (lang && typeof hljs !== 'undefined') {
                        const code = document.createElement('code');
                        code.className = 'language-' + lang;
                        code.textContent = body;
                        pre.appendChild(code);
                        hljs.highlightElement(code);
                    } else {
                        pre.textContent = body;
                    }
                }
            } else {
                if (pre) pre.style.display = 'none';
                const emptyBody = area.querySelector('p.response-empty.box');
                if (emptyBody) emptyBody.style.display = '';
            }
            // apply body links — wrap matching JSON values as clickable links
            if (body && body.trim() && contentType && contentType.includes('json') && pre) {
                applyBodyLinks(pre, form, body, String(status));
            }
            // move response-info into the mode-row
            var responseInfo = area.querySelector('.response-info');
            var modeRow = document.querySelector('.mode-row');
            if (responseInfo && modeRow) {
                // remove any previous response-info from the mode-row
                var oldInfo = modeRow.querySelector('.response-info');
                if (oldInfo) oldInfo.remove();
                modeRow.appendChild(responseInfo);
            }
        }

        function fetchFragment(url) {
            if (fragmentCache.has(url)) return Promise.resolve(fragmentCache.get(url));
            return fetch(url).then(function(resp) {
                if (!resp.ok) return null;
                return resp.text().then(function(html) {
                    fragmentCache.set(url, html);
                    return html;
                });
            });
        }

        if (status === 0) {
            isFallback = true;
            return fetchFragment(fallbackUrl).then(function(html) {
                if (html) populate(html);
            });
        }
        return fetchFragment(specificUrl).then(function(html) {
            if (html) return populate(html);
            isFallback = true;
            return fetchFragment(fallbackUrl).then(function(html) {
                if (html) populate(html);
            });
        });
    }

    function applyBodyLinks(pre, form, body, statusCode) {
        var linksAttr = form.getAttribute('data-response-links');
        if (!linksAttr) return;
        var allLinks;
        try { allLinks = JSON.parse(linksAttr); } catch(e) { return; }
        var statusLinks = allLinks[statusCode];
        if (!statusLinks) return;

        var parsed;
        try { parsed = JSON.parse(body); } catch(e) { return; }

        // Build a map: JSON pointer path → [{operationId, paramName, value}]
        var pointerMap = {};
        for (var linkName in statusLinks) {
            var link = statusLinks[linkName];
            if (!link.parameters) continue;
            for (var paramName in link.parameters) {
                var expr = link.parameters[paramName];
                var match = expr.match(/^\$response\.body#\/(.+)$/);
                if (!match) continue;
                var pointer = match[1];
                if (pointer.indexOf('[*]') >= 0) {
                    // Expand wildcard expressions into indexed pointer entries
                    expandWildcardPointer(pointer.split('/'), 0, parsed, [], pointerMap, link, paramName, linkName);
                } else {
                    // Standard pointer resolution (no wildcards)
                    var parts = pointer.split('/');
                    var val = parsed;
                    for (var i = 0; i < parts.length; i++) {
                        if (val === undefined || val === null) break;
                        val = val[parts[i]];
                    }
                    if (val === undefined || val === null) continue;
                    if (!pointerMap[pointer]) pointerMap[pointer] = [];
                    pointerMap[pointer].push({
                        operationId: link.operationId,
                        paramName: paramName,
                        value: String(val),
                        linkName: linkName
                    });
                }
            }
        }
        if (Object.keys(pointerMap).length === 0) return;

        // Render JSON with links
        var codeEl = pre.querySelector('code') || pre;
        codeEl.innerHTML = renderJsonWithLinks(parsed, pointerMap, []);
    }

    function expandWildcardPointer(segments, segIdx, current, pathSoFar, pointerMap, link, paramName, linkName) {
        if (current === undefined || current === null) return;
        if (segIdx >= segments.length) {
            var pointer = pathSoFar.join('/');
            if (!pointerMap[pointer]) pointerMap[pointer] = [];
            pointerMap[pointer].push({
                operationId: link.operationId,
                paramName: paramName,
                value: String(current),
                linkName: linkName
            });
            return;
        }
        var seg = segments[segIdx];
        if (seg.endsWith('[*]')) {
            // Property access + array wildcard: e.g. "visits[*]" or bare "[*]" for top-level arrays
            var prop = seg.substring(0, seg.length - 3);
            var arr = prop ? current[prop] : current;
            if (!Array.isArray(arr)) return;
            for (var i = 0; i < arr.length; i++) {
                var pathParts = prop ? pathSoFar.concat(prop, String(i)) : pathSoFar.concat(String(i));
                expandWildcardPointer(segments, segIdx + 1, arr[i], pathParts, pointerMap, link, paramName, linkName);
            }
        } else {
            // Regular property access
            expandWildcardPointer(segments, segIdx + 1, current[seg], pathSoFar.concat(seg), pointerMap, link, paramName, linkName);
        }
    }

    function renderJsonWithLinks(value, pointerMap, path) {
        if (value === null) return '<span class="hljs-literal">null</span>';
        if (typeof value === 'boolean') return '<span class="hljs-literal">' + value + '</span>';
        if (typeof value === 'number') {
            var pointer = path.join('/');
            var entries = pointerMap[pointer];
            var valueHtml = '<span class="hljs-number">' + value + '</span>';
            if (entries && entries.length > 0) {
                return valueHtml + entries.map(renderLinkBadge).join('');
            }
            return valueHtml;
        }
        if (typeof value === 'string') {
            var pointer = path.join('/');
            var entries = pointerMap[pointer];
            var escaped = escapeHtml(value);
            var valueHtml = '<span class="hljs-string">"' + escaped + '"</span>';
            if (entries && entries.length > 0) {
                return valueHtml + entries.map(renderLinkBadge).join('');
            }
            return valueHtml;
        }
        if (Array.isArray(value)) {
            if (value.length === 0) return '<span class="hljs-punctuation">[]</span>';
            var items = value.map(function(item, i) {
                return renderJsonWithLinks(item, pointerMap, path.concat(String(i)));
            });
            return '<span class="hljs-punctuation">[</span>\n'
                + items.map(function(item) { return indentStr(path.length + 1) + item; }).join('<span class="hljs-punctuation">,</span>\n')
                + '\n' + indentStr(path.length) + '<span class="hljs-punctuation">]</span>';
        }
        if (typeof value === 'object') {
            var keys = Object.keys(value);
            if (keys.length === 0) return '<span class="hljs-punctuation">{}</span>';
            var pairs = keys.map(function(key) {
                var childPath = path.concat(key);
                var keyHtml = '<span class="hljs-attr">"' + escapeHtml(key) + '"</span>';
                var valHtml = renderJsonWithLinks(value[key], pointerMap, childPath);
                return indentStr(path.length + 1) + keyHtml + '<span class="hljs-punctuation">:</span> ' + valHtml;
            });
            return '<span class="hljs-punctuation">{</span>\n'
                + pairs.join('<span class="hljs-punctuation">,</span>\n')
                + '\n' + indentStr(path.length) + '<span class="hljs-punctuation">}</span>';
        }
        return String(value);
    }

    function renderLinkBadge(entry) {
        var target = window._operationIdMap ? window._operationIdMap[entry.operationId] : null;
        if (!target) return '';
        var href = '#' + target.path + '/' + target.method + '?' + encodeURIComponent(entry.paramName) + '=' + encodeURIComponent(entry.value);
        return ' <a class="body-link" href="' + escapeHtml(href) + '" tabindex="0">' + escapeHtml(entry.linkName) + '</a>';
    }

    function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function indentStr(level) {
        var s = '';
        for (var i = 0; i < level; i++) s += '  ';
        return s;
    }

    function appendHeaderRow(grid, name, value) {
        const nameEl = document.createElement('span');
        nameEl.className = 'response-header-name';
        nameEl.textContent = name;
        const valueEl = document.createElement('span');
        valueEl.className = 'response-header-value';
        valueEl.setAttribute('data-header', name.toLowerCase());
        valueEl.textContent = value;
        grid.appendChild(nameEl);
        grid.appendChild(valueEl);
    }

    // event delegation for response-area toggle/show-all clicks
    detail.addEventListener('keydown', function(e) {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        const toggle = e.target.closest('.response-headers-toggle');
        if (toggle) {
            e.preventDefault();
            toggle.click();
        }
    });
    detail.addEventListener('click', function(e) {
        const toggle = e.target.closest('.response-headers-toggle');
        if (toggle) {
            const area = toggle.closest('.response-area');
            if (!area) return;
            const container = toggle.closest('.response-headers');
            if (container) {
                const expanded = container.classList.toggle('is-expanded');
                const count = toggle.textContent.match(/\((\d+)\)/);
                const num = count ? count[1] : '';
                toggle.textContent = 'Headers' + (num ? ' (' + num + ') ' : ' ') + (expanded ? '▼' : '▶');
            }
            return;
        }
        const showAll = e.target.closest('.response-headers-show-all');
        if (showAll) {
            const area = showAll.closest('.response-area');
            if (!area) return;
            const undocGrid = area.querySelector('.response-headers-undocumented');
            if (undocGrid) {
                const visible = undocGrid.style.display !== 'none';
                undocGrid.style.display = visible ? 'none' : '';
                const count = showAll.textContent.match(/\((\d+)/);
                showAll.textContent = visible
                    ? 'Show all (' + (count ? count[1] : '') + ' more)'
                    : 'Show less';
            }
            return;
        }
    });

    function showCopied(btn) {
        const original = btn.textContent;
        btn.textContent = 'Copied!';
        if (modeContainer) modeContainer.focus();
        setTimeout(function() { btn.textContent = original; }, 1500);
    }

    // Pin shortcut (Ctrl+P / Alt+P) — works in both detail pane and global headers
    document.addEventListener('keydown', function(e) {
        const persistMod = /Mac/.test(navigator.platform) ? e.ctrlKey : e.altKey;
        if (e.code === 'KeyP' && persistMod) {
            const el = document.activeElement;
            const fieldEl = el.closest('.field');
            let persistBtn = fieldEl ? fieldEl.querySelector('.persist-toggle') : null;
            if (!persistBtn) {
                const row = el.closest('.custom-header-row');
                persistBtn = row ? row.querySelector('.persist-toggle') : null;
            }
            if (persistBtn) {
                persistBtn.click();
                e.preventDefault();
                e.stopPropagation();
            }
        }
    });

    function findSpatialTarget(el, direction) {
        const rect = el.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        var openServerDropdown = el.closest && el.closest('#server-selector.is-active');
        var candidateSelector = openServerDropdown
            ? '#server-selector .dropdown-content input, #server-selector .dropdown-content button'
            : '#method-content input, #method-content select, #method-content textarea, #method-content button, #method-content [tabindex="0"],'
              + ' .tabs a[tabindex="0"],'
              + ' [data-toggle="mode"],'
              + ' [role="tree"]';
        const candidates = Array.from(document.querySelectorAll(candidateSelector)).filter(function(c) {
            if (c === el) return false;
            if (c.disabled) return false;
            if (!c.offsetParent && c.getAttribute('role') !== 'tree') return false;
            if (c.closest && c.closest('.toggle') && c.getAttribute('data-toggle') !== 'mode') return false;
            // when navigating from outside tabs, only the active tab is a candidate
            const elIsTab = el.closest && el.closest('.tabs');
            if (!elIsTab && c.closest && c.closest('.tabs') && !c.closest('li').classList.contains('is-active')) return false;
            const elIsStatusTab = el.classList.contains('schema-status-tab');
            if (!elIsStatusTab && c.classList.contains('schema-status-tab') && !c.classList.contains('is-active')) return false;
            return true;
        });

        let best = null;
        let bestDist = Infinity;
        let bestCorridor = false;
        const elIsTab = el.closest && el.closest('.tabs');
        const elIsStatusTab = el.classList.contains('schema-status-tab');

        candidates.forEach(function(c) {
            let candidateRect = c.getBoundingClientRect();
            // for tab links, use the tab bar's rect so the whole bar acts as one spatial unit
            if (!elIsTab && c.closest && c.closest('.tabs')) candidateRect = c.closest('.tabs').getBoundingClientRect();
            if (!elIsStatusTab && c.classList.contains('schema-status-tab')) candidateRect = c.closest('.schema-status-tabs').getBoundingClientRect();
            const candidateCenterX = candidateRect.left + candidateRect.width / 2;
            const candidateCenterY = candidateRect.top + candidateRect.height / 2;

            // filter by direction
            if (direction === 'down' && candidateCenterY <= centerY) return;
            if (direction === 'up' && candidateCenterY >= centerY) return;
            if (direction === 'right' && candidateCenterX <= centerX) return;
            if (direction === 'left' && candidateCenterX >= centerX) return;

            // check corridor overlap on cross-axis
            let corridor;
            if (direction === 'down' || direction === 'up') {
                corridor = rect.right > candidateRect.left && candidateRect.right > rect.left; // horizontal overlap
            } else {
                corridor = rect.bottom > candidateRect.top && candidateRect.bottom > rect.top; // vertical overlap
            }

            // distance: primary axis for corridor, euclidean for fallback
            let dist;
            if (corridor) {
                dist = (direction === 'down' || direction === 'up') ? Math.abs(candidateCenterY - centerY) : Math.abs(candidateCenterX - centerX);
            } else {
                dist = Math.sqrt((candidateCenterX - centerX) * (candidateCenterX - centerX) + (candidateCenterY - centerY) * (candidateCenterY - centerY));
            }

            // corridor candidates beat non-corridor
            if (corridor && !bestCorridor) {
                best = c;
                bestDist = dist;
                bestCorridor = true;
            } else if (corridor === bestCorridor && dist < bestDist) {
                best = c;
                bestDist = dist;
            }
        });

        return best;
    }

    // Spatial arrow-key navigation for detail pane and tabs
    document.addEventListener('keydown', function(e) {
        const el = document.activeElement;
        if (!el || !el.closest) return;
        // don't interfere with tree or toggle internal navigation
        if (el.closest('[role="tree"]')) return;
        // mode toggle: let spatial nav handle ArrowUp/ArrowDown, but leave ArrowLeft/Right to toggle
        if (el.matches('[data-toggle="mode"]') && (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'Home' || e.key === 'End')) return;
        if (el.closest('.toggle') && !el.matches('[data-toggle="mode"]')) return;
        // let the server dropdown handle its own keyboard navigation
        var serverSel = el.closest('#server-selector');
        if (serverSel && serverSel.classList.contains('is-active')) return;

        // Tab/Shift+Tab on status code tabs: move to next/prev non-status-tab element
        if (e.key === 'Tab' && el.classList.contains('schema-status-tab')) {
            e.preventDefault();
            // Find all focusable elements
            var allFocusable = Array.from(document.querySelectorAll(
                'input, select, textarea, button, a[tabindex="0"], [tabindex="0"]'
            )).filter(function(f) {
                return f.offsetParent !== null && !f.disabled;
            });
            var currentIndex = allFocusable.indexOf(el);
            if (currentIndex >= 0) {
                // Find next/prev element that's not a status tab (also skip toggle container)
                var delta = e.shiftKey ? -1 : 1;
                for (var i = currentIndex + delta; i >= 0 && i < allFocusable.length; i += delta) {
                    var candidate = allFocusable[i];
                    if (candidate.classList.contains('schema-status-tab')) continue;
                    if (candidate.closest && candidate.closest('.toggle') && candidate.getAttribute('data-toggle') !== 'mode') continue;
                    candidate.focus();
                    return;
                }
            }
            return;
        }

        // Tab/Shift+Tab on a tab: jump into content or back to tree
        if (e.key === 'Tab' && el.closest('.tabs')) {
            e.preventDefault();
            if (e.shiftKey) {
                var tree = document.querySelector('[role="tree"]');
                if (tree) tree.focus();
            } else {
                var target = findSpatialTarget(el, 'down');
                if (target) target.focus();
            }
            return;
        }

        // Shift+Tab from FIRST content element: go to the active tab, not the last one
        if (e.key === 'Tab' && e.shiftKey && (el.closest('#method-content') || el.closest('#detail'))) {
            // Check if we're at the first element by seeing if going down from tabs reaches us
            var activeTabLink = document.querySelector('.tabs .is-active a');
            if (activeTabLink) {
                var firstContentElement = findSpatialTarget(activeTabLink, 'down');
                if (firstContentElement === el) {
                    // We're at the first content element - jump back to tab
                    e.preventDefault();
                    activeTabLink.focus();
                    return;
                }
            }
        }

        // Shift+Tab into status tabs: focus the active status tab, not the last one in DOM order
        if (e.key === 'Tab' && e.shiftKey && (el.closest('#method-content') || el.closest('#detail') || el.classList.contains('mode-send-button'))) {
            var allFocusable = Array.from(document.querySelectorAll(
                'input, select, textarea, button, a[tabindex="0"], [tabindex="0"]'
            )).filter(function(f) {
                return f.offsetParent !== null && !f.disabled;
            });
            var currentIndex = allFocusable.indexOf(el);
            if (currentIndex > 0) {
                // Scan backward to find the previous content element (skip toggle internals)
                for (var pi = currentIndex - 1; pi >= 0; pi--) {
                    var prevElement = allFocusable[pi];
                    if (prevElement.closest && prevElement.closest('.toggle')) continue;
                    if (prevElement.classList.contains('schema-status-tab')) {
                        var activeStatusTab = prevElement.closest('.schema-status-tabs').querySelector('.schema-status-tab.is-active');
                        if (activeStatusTab) {
                            e.preventDefault();
                            activeStatusTab.focus();
                            return;
                        }
                    }
                    break;
                }
            }
        }

        const isArrow = ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].indexOf(e.key) >= 0;
        if (!isArrow && e.key !== 'Enter' && e.key !== ' ' && e.key !== 'Escape') return;

        // text input/textarea: Left/Right stay native
        if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight')
            && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) return;

        // textarea multi-line: Up/Down navigate away only at first/last line
        if (el.tagName === 'TEXTAREA' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
            const val = el.value;
            const pos = el.selectionStart;
            if (e.key === 'ArrowDown') {
                if (val.indexOf('\n', pos) >= 0) return; // not at last line
            } else {
                if (val.lastIndexOf('\n', pos - 1) >= 0) return; // not at first line
            }
        }

        if (isArrow) {
            const direction = e.key === 'ArrowDown' ? 'down'
                : e.key === 'ArrowUp' ? 'up'
                : e.key === 'ArrowRight' ? 'right' : 'left';
            const target = findSpatialTarget(el, direction);
            if (target) {
                target.focus();
            } else {
                const bumpDir = (direction === 'left' || direction === 'right') ? 'h' : 'v';
                bump(el, bumpDir);
            }
            e.preventDefault();
            e.stopPropagation();
        } else if (e.key === 'Enter' || e.key === ' ') {
            var link = (el.tagName === 'A' && el.hasAttribute('href')) ? el
                : el.querySelector && el.querySelector('a[href]');
            if (link) {
                link.click();
            } else if (e.key === 'Enter' && el.classList.contains('schema-toggle')) {
                el.click();
            } else if (e.key === 'Enter' && el.tagName === 'BUTTON') {
                el.click();
            } else if (el.classList.contains('custom-header-remove')) {
                el.click();
            } else if (e.key === 'Enter' && el.tagName !== 'SELECT') {
                const sendBtn = document.querySelector('.mode-send-button');
                if (sendBtn) sendBtn.click();
            } else {
                return;
            }
            e.preventDefault();
            e.stopPropagation();
        } else if (e.key === 'Escape') {
            const tree = document.querySelector('[role="tree"]');
            if (tree) tree.focus();
            e.preventDefault();
            e.stopPropagation();
        }
    }, true);

    // Activate method tabs on focus (spatial navigation focuses them)
    document.addEventListener('focusin', function(e) {
        const link = e.target.closest('.tabs a');
        if (!link) return;
        const li = link.closest('li');
        if (!li || li.classList.contains('is-active')) return;
        const tabs = li.closest('.tabs');
        tabs.querySelectorAll('li').forEach(function(l) { l.classList.remove('is-active'); });
        li.classList.add('is-active');
        clearPreviousResponse();
        const hxGet = link.getAttribute('hx-get');
        htmx.ajax('GET', hxGet, link.getAttribute('hx-target'));
        history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
    });

    // Activate status code tabs on focus
    document.addEventListener('focusin', function(e) {
        if (!e.target.classList.contains('schema-status-tab')) return;
        if (e.target.classList.contains('is-active')) return;
        activateStatusTab(e.target);
    });

    // When clicking a method badge in the tree, navigate to that tab
    let pendingMethod = null;
    document.addEventListener('click', function(e) {
        const badge = e.target.closest('[role="treeitem"] .tag');
        if (!badge) return;
        pendingMethod = badge.textContent.trim();
    }, true);


    // Navigate from URL hash or auto-load first operation
    const HTTP_METHODS = ['GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS','TRACE'];
    window._hashNavPending = false;
    function navigateFromHash() {
        var fullRoute = decodeURIComponent(location.hash.replace(/^#/, ''));
        if (!fullRoute) return false;

        // Parse query parameters from hash
        var queryParams = {};
        var qIndex = fullRoute.indexOf('?');
        var route;
        if (qIndex >= 0) {
            var queryString = fullRoute.substring(qIndex + 1);
            route = fullRoute.substring(0, qIndex);
            new URLSearchParams(queryString).forEach(function(value, key) {
                queryParams[key] = value;
            });
        } else {
            route = fullRoute;
        }
        // Parse [tag] prefix for tag tree navigation
        let tagFromHash = null;
        const tagMatch = route.match(/^\[([^\]]+)\](.*)/);
        if (tagMatch) {
            tagFromHash = tagMatch[1];
            route = tagMatch[2];
        }
        const parts = route.split('/');
        const last = parts[parts.length - 1];
        let methodFromHash = null;
        let treePath = route;
        if (HTTP_METHODS.indexOf(last) >= 0) {
            methodFromHash = last;
            treePath = parts.slice(0, -1).join('/');
        }
        let hxGet, hxEl;
        if (tagFromHash) {
            // Tag tree: hx-get is "path/METHOD.html", find by data-tag + hx-get
            hxGet = route + '.html';
            hxEl = document.querySelector('[data-tag="' + tagFromHash + '"][hx-get="' + hxGet + '"]');
            if (!hxEl) {
                // Tag tree may not be loaded yet — switch to tags view; afterSettle will retry
                window._hashNavPending = true;
                const vt = document.querySelector('[data-toggle="view"]');
                if (vt) {
                    const tagsBtn = document.querySelector('[data-toggle-value="tags"]');
                    if (tagsBtn && !tagsBtn.classList.contains('is-active')) vt._select('tags');
                }
                return true;
            }
        } else {
            // Path tree: hx-get is "path/index.html"
            hxGet = treePath + '/index.html';
            hxEl = document.querySelector('[hx-get="' + hxGet + '"]');
            if (!hxEl) return false;
        }
        const item = hxEl.closest('[role="treeitem"]');
        const tree = document.querySelector('[role="tree"]');
        if (item && tree) {
            tree._expandParentsOf(item);
            tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) { el.removeAttribute('aria-selected'); });
            item.setAttribute('aria-selected', 'true');
        }
        if (methodFromHash && !tagFromHash) pendingMethod = methodFromHash;
        if (Object.keys(queryParams).length > 0) {
            window._pendingParams = queryParams;
        }
        htmx.ajax('GET', hxGet, {target: '#detail', swap: 'innerHTML'});
        return true;
    }
    if (!navigateFromHash()) {
        const firstHxEl = document.querySelector('#tree-container [hx-get]');
        if (firstHxEl) {
            const hxGet = firstHxEl.getAttribute('hx-get');
            htmx.ajax('GET', hxGet, '#detail');
            const route = hxGetToRoute(hxGet);
            const tag = firstHxEl.getAttribute('data-tag');
            const hash = tag ? '#[' + tag + ']' + route : '#' + route;
            history.replaceState(null, '', hash);
        }
    }
    // Disable browser's automatic scroll restoration — we handle it manually
    if ('scrollRestoration' in history) history.scrollRestoration = 'manual';

    window.addEventListener('popstate', function(e) {
        if (e.state && e.state.focusSelector) {
            window._pendingRestoreFocus = e.state.focusSelector;
        } else {
            window._pendingFocusFirst = true;
        }
        if (e.state && e.state.scrollY != null) {
            window._pendingScrollY = e.state.scrollY;
        }
        navigateFromHash();
    });

    // Intercept clicks on hash links (schema links, body links) inside detail
    if (detail) {
        detail.addEventListener('click', function(e) {
            var link = e.target.closest('a[href^="#"]');
            if (!link) return;
            e.preventDefault();
            // Save clicked link and scroll position so back navigation can restore them
            history.replaceState({focusSelector: 'a[href="' + link.getAttribute('href') + '"]', scrollY: window.scrollY}, '');
            history.pushState(null, '', link.getAttribute('href'));
            window._pendingFocusFirst = true;
            navigateFromHash();
        });
    }

    // Send button handler (delegated from detail pane)
    if (detail) {
        detail.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && e.target.closest('form[data-path]') && e.target.tagName !== 'BUTTON') {
                e.preventDefault();
            }
        });
        detail.addEventListener('submit', function(e) {
            e.preventDefault();
            const sendForm = e.target.closest('form[data-path]');
            if (!sendForm) return;
            const sendBtn = document.querySelector('.mode-send-button');

            const pathTemplate = sendForm.getAttribute('data-path');
            const method = sendForm.getAttribute('data-method');
            const modeEl = document.querySelector('[data-toggle="mode"]');
            const mode = modeEl ? modeEl.getAttribute('data-mode') : 'try';
            const baseUrl = modeEl ? (modeEl.getAttribute('data-base-url') || '') : '';

            // Collect input values
            const inputs = sendForm.querySelectorAll('input[name], select[name]');
            let resolvedPath = pathTemplate;
            const queryParams = [];
            let requestHeaders = {};
            const cookieParts = [];
            inputs.forEach(function(inp) {
                const name = inp.getAttribute('name');
                const paramIn = inp.getAttribute('data-param-in') || 'query';
                const isCheckbox = inp.type === 'checkbox';
                const val = isCheckbox ? (inp.checked ? 'true' : '') : inp.value;
                if (paramIn === 'path' || pathTemplate.includes('{' + name + '}')) {
                    resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
                } else if (paramIn === 'header' || paramIn === 'auth-header') {
                    if (val) {
                        if (name === 'Authorization' && !val.startsWith('Bearer ')) {
                            requestHeaders[name] = 'Bearer ' + val;
                        } else {
                            requestHeaders[name] = val;
                        }
                    }
                } else if (paramIn === 'cookie') {
                    if (val) cookieParts.push(name + '=' + val);
                } else if (paramIn === 'auth-query') {
                    if (val) queryParams.push(name + '=' + encodeURIComponent(val));
                } else if (val) {
                    queryParams.push(name + '=' + encodeURIComponent(val));
                }
            });
            if (cookieParts.length > 0) requestHeaders['Cookie'] = cookieParts.join('; ');
            sendForm.querySelectorAll('.custom-header-row').forEach(function(row) {
                const name = row.querySelector('.custom-header-name').value.trim();
                const value = row.querySelector('.custom-header-value').value;
                if (name) requestHeaders[name] = value;
            });
            // Collect global headers (lowest priority) and merge
            const globalHeaders = {};
            const ghPanel = document.getElementById('global-headers');
            if (ghPanel) {
                ghPanel.querySelectorAll('.custom-header-row').forEach(function(row) {
                    const name = row.querySelector('.custom-header-name').value.trim();
                    const value = row.querySelector('.custom-header-value').value;
                    if (name) globalHeaders[name] = value;
                });
            }
            const mergedHeaders = {};
            Object.keys(globalHeaders).forEach(function(h) { mergedHeaders[h] = globalHeaders[h]; });
            Object.keys(requestHeaders).forEach(function(h) { mergedHeaders[h] = requestHeaders[h]; });
            requestHeaders = mergedHeaders;
            let url = baseUrl.startsWith('http') ? baseUrl + resolvedPath
                    : new URL((baseUrl + resolvedPath).replace(/\/+/g, '/'), window.location.origin).href;
            if (queryParams.length > 0) url += '?' + queryParams.join('&');

            const bodyTextarea = sendForm.querySelector('textarea[data-request-body]');
            const bodyValue = bodyTextarea ? bodyTextarea.value : '';
            
            // Check for form-encoded fields (exclude parameter inputs by checking for data-param-in attribute)
            const allNamedInputs = sendForm.querySelectorAll('input[name]');
            const formFields = Array.from(allNamedInputs).filter(function(field) {
                // Exclude parameter inputs (they have data-param-in attribute)
                if (field.hasAttribute('data-param-in')) return false;
                // Exclude custom header inputs (handled separately above)
                if (field.closest('.custom-header-row')) return false;
                // Include non-checkbox inputs
                if (field.type !== 'checkbox') return true;
                // Include only checked checkboxes
                return field.checked;
            });
            const hasFormFields = formFields.length > 0;
            let formEncodedBody = '';
            let contentType = 'application/json';
            
            if (hasFormFields) {
                const params = new URLSearchParams();
                formFields.forEach(function(field) {
                    if (field.type === 'checkbox') {
                        params.append(field.name, 'true');
                    } else {
                        params.append(field.name, field.value);
                    }
                });
                formEncodedBody = params.toString();
                contentType = 'application/x-www-form-urlencoded';
            }

            // Parse embedded schema JSON
            let schema = null;
            const schemaScript = sendForm.querySelector('script.operation-schema');
            if (schemaScript && schemaScript.textContent) {
                try {
                    schema = JSON.parse(schemaScript.textContent);
                } catch (e) {
                    console.warn('Failed to parse operation schema JSON:', e);
                }
            }

            // Use generator registry for copy modes
            const generator = generators[mode];
            if (generator) {
                const params = {
                    method: method,
                    url: url,
                    headers: requestHeaders,
                    body: hasFormFields ? formEncodedBody : bodyValue,
                    contentType: contentType,
                    schema: schema
                };
                const cmd = generator(params);
                navigator.clipboard.writeText(cmd);
                showCopied(sendBtn);
            } else if (mode === 'try') {
                sendBtn.disabled = true;
                sendBtn.textContent = 'Sending...';
                const fetchOptions = { method: method, headers: {} };
                if (hasFormFields) {
                    fetchOptions.body = formEncodedBody;
                    fetchOptions.headers['Content-Type'] = contentType;
                } else if (bodyValue) {
                    fetchOptions.body = bodyValue;
                    fetchOptions.headers['Content-Type'] = contentType;
                }
                const acceptSelect = detail.querySelector('[data-accept] select');
                if (acceptSelect && acceptSelect.value) {
                    fetchOptions.headers['Accept'] = acceptSelect.value;
                }
                Object.keys(requestHeaders).forEach(function(h) {
                    if (h !== 'Cookie') fetchOptions.headers[h] = requestHeaders[h];
                });
                const area = sendForm.querySelector('.response-area');
                fetch(url, fetchOptions).then(function(resp) {
                    const contentType = resp.headers.get('Content-Type') || '';
                    const headers = [];
                    resp.headers.forEach(function(value, name) {
                        headers.push({name: name, value: value});
                    });
                    return resp.text().then(function(text) {
                        let headersWereVisible = area.querySelector('.response-headers.is-expanded') !== null;
                        if (!area.querySelector('.response-headers')) headersWereVisible = null; // auto-detect
                        if (contentType.includes('json')) {
                            try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                        } else if (contentType.includes('xml')) {
                            try { text = prettyPrintXml(text); } catch(e) {}
                        }
                        return showResponse(area, sendForm, resp.status, resp.statusText, headers, text, contentType, headersWereVisible).then(function() {
                            const currentlyVisible = area.querySelector('.response-headers.is-expanded') !== null;
                            responseCache.set(opKey(sendForm), {
                                status: resp.status, statusText: resp.statusText,
                                headers: headers, headersExpanded: currentlyVisible,
                                body: text, ct: contentType
                            });
                        });
                    });
                }).catch(function(err) {
                    return showResponse(area, sendForm, 0, 'Network error', [], err.message, '', false);
                }).finally(function() {
                    if (sendBtn) {
                        sendBtn.disabled = false;
                        sendBtn.textContent = 'Send';
                    }
                    if (modeContainer) modeContainer.focus();
                });
            }
        });
    }
    // htmx error retry with banner
    const banner = document.getElementById('error-banner');
    let activeRetries = 0;
    function showBanner() {
        activeRetries++;
        banner.style.display = '';
    }
    function hideBannerIfDone() {
        activeRetries--;
        if (activeRetries <= 0) {
            activeRetries = 0;
            banner.style.display = 'none';
        }
    }
    function retryHtmx(url, targetEl) {
        showBanner();
        const interval = setInterval(function() {
            fetch(url).then(function(resp) {
                if (!resp.ok) return;
                clearInterval(interval);
                return resp.text().then(function(html) {
                    if (targetEl) targetEl.innerHTML = html;
                    hideBannerIfDone();
                    if (typeof htmx !== 'undefined') htmx.process(targetEl);
                });
            }).catch(function() {});
        }, 1000);
    }
    function htmxErrorUrl(e) {
        return (e.detail.pathInfo && e.detail.pathInfo.requestPath)
            || (e.detail.elt && e.detail.elt.getAttribute('hx-get'));
    }
    document.body.addEventListener('htmx:oobAfterSwap', function(e) {
        const target = e.detail.target;
        if (target && target.id === 'server-override') {
            const serverSelector = document.getElementById('server-selector');
            if (!serverSelector) return;
            
            const hasOverride = target.children.length > 0;
            // Disable/enable global radio inputs (template presets)
            const globalInputs = serverSelector.querySelectorAll('input[type="radio"]:not([name="server-override"])');
            globalInputs.forEach(function(input) {
                input.disabled = hasOverride;
            });
            if (hasOverride) {
                const overrideRadios = target.querySelectorAll('input[type="radio"]');
                const selectedOverride = target.querySelector('input[type="radio"]:checked');
                if (selectedOverride) {
                    updateBaseUrl(selectedOverride.value);
                }
                overrideRadios.forEach(function(radio) {
                    radio.addEventListener('change', function() {
                        updateBaseUrl(radio.value);
                    });
                });
            } else {
                // Restore from checked radio
                var selectedRadio = serverSelector.querySelector('input[type="radio"][name="server"]:checked');
                if (selectedRadio) updateBaseUrl(selectedRadio.value);
            }
        }
    });

    function updateFilterStatusLine() {
        const tree = document.querySelector('[role="tree"]');
        const statusLine = document.querySelector('.filter-status-line');
        if (!tree || !statusLine) return;
        
        const filterClass = Array.from(tree.classList).find(cls => cls.startsWith('filter-'));
        if (!filterClass) {
            statusLine.textContent = '';
            return;
        }
        
        const allOperations = tree.querySelectorAll('[role="treeitem"]').length;
        const visibleOperations = tree.querySelectorAll('[role="treeitem"]:not([style*="display: none"])').length;
        statusLine.textContent = 'Showing ' + visibleOperations + ' of ' + allOperations + ' operations';
    }
    
    function initFilterIcon() {
        const filterIcon = document.querySelector('.filter-icon');
        if (!filterIcon) return;
        if (filterIcon.dataset.initialized) return;
        filterIcon.dataset.initialized = 'true';
        
        const filterPanel = document.querySelector('.filter-pill-panel');
        const tree = document.querySelector('[role="tree"]');
        if (!filterPanel || !tree) return;
        
        // Restore panel state from localStorage
        const panelOpen = localStorage.getItem('openapi-ui-tag-filter-open') === 'true';
        if (panelOpen) {
            filterPanel.classList.add('is-active');
        }
        
        // Restore selected tag from localStorage
        const savedTag = localStorage.getItem('openapi-ui-tag-filter');
        if (savedTag) {
            tree.classList.add('filter-' + savedTag);
            filterIcon.classList.add('is-active');
            updateFilterStatusLine();
        }
        
        filterIcon.addEventListener('click', function() {
            filterPanel.classList.toggle('is-active');
            localStorage.setItem('openapi-ui-tag-filter-open', filterPanel.classList.contains('is-active'));
        });
        
        filterIcon.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                const wasOpen = filterPanel.classList.contains('is-active');
                filterPanel.classList.toggle('is-active');
                localStorage.setItem('openapi-ui-tag-filter-open', filterPanel.classList.contains('is-active'));
                
                // When opening panel, focus first pill or previously selected pill
                if (!wasOpen && filterPanel.classList.contains('is-active')) {
                    const pills = filterPanel.querySelectorAll('.tag');
                    const activePill = Array.from(pills).find(function(p) {
                        const tagClass = Array.from(p.classList).find(cls => cls.startsWith('tag-'));
                        if (!tagClass) return false;
                        const tagName = tagClass.substring(4);
                        return tree.classList.contains('filter-' + tagName);
                    });
                    const firstPill = activePill || pills[0];
                    if (firstPill) firstPill.focus();
                }
            }
        });
        
        // Pill click and keyboard handlers
        const pills = Array.from(filterPanel.querySelectorAll('.tag'));
        
        function activatePillFilter(pill) {
            const tagClass = Array.from(pill.classList).find(cls => cls.startsWith('tag-'));
            if (!tagClass) return;
            
            const tagName = tagClass.substring(4);
            const filterClass = 'filter-' + tagName;
            
            // Remove any existing filter
            tree.className = tree.className.replace(/\bfilter-\S+/g, '').trim();
            // Add new filter
            tree.classList.add(filterClass);
            filterIcon.classList.add('is-active');
            localStorage.setItem('openapi-ui-tag-filter', tagName);
            updateFilterStatusLine();
        }
        
        function deactivatePillFilter() {
            tree.className = tree.className.replace(/\bfilter-\S+/g, '').trim();
            filterIcon.classList.remove('is-active');
            localStorage.setItem('openapi-ui-tag-filter', '');
            updateFilterStatusLine();
        }
        
        pills.forEach(function(pill) {
            pill.addEventListener('click', function() {
                const tagClass = Array.from(pill.classList).find(cls => cls.startsWith('tag-'));
                if (!tagClass) return;
                
                const tagName = tagClass.substring(4);
                const filterClass = 'filter-' + tagName;
                
                // Single-select: if this filter is already active, deselect it
                if (tree.classList.contains(filterClass)) {
                    deactivatePillFilter();
                } else {
                    activatePillFilter(pill);
                }
            });
            
            pill.addEventListener('keydown', function(e) {
                const currentIndex = pills.indexOf(pill);
                
                if (e.key === 'ArrowLeft') {
                    e.preventDefault();
                    if (currentIndex > 0) {
                        const prevPill = pills[currentIndex - 1];
                        prevPill.focus();
                        activatePillFilter(prevPill);
                    }
                } else if (e.key === 'ArrowRight') {
                    e.preventDefault();
                    if (currentIndex < pills.length - 1) {
                        const nextPill = pills[currentIndex + 1];
                        nextPill.focus();
                        activatePillFilter(nextPill);
                    }
                } else if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    const tagClass = Array.from(pill.classList).find(cls => cls.startsWith('tag-'));
                    if (!tagClass) return;
                    const tagName = tagClass.substring(4);
                    const filterClass = 'filter-' + tagName;
                    
                    // If this pill's filter is active, deselect it
                    if (tree.classList.contains(filterClass)) {
                        deactivatePillFilter();
                    }
                } else if (e.key === 'Escape') {
                    e.preventDefault();
                    filterPanel.classList.remove('is-active');
                    localStorage.setItem('openapi-ui-tag-filter-open', 'false');
                    filterIcon.focus();
                } else if (e.key === 'ArrowDown' || e.key === 'Tab') {
                    if (!e.shiftKey) {
                        e.preventDefault();
                        const tree = document.querySelector('[role="tree"]');
                        if (tree) tree.focus();
                    }
                } else if (e.key === 'ArrowUp' || (e.key === 'Tab' && e.shiftKey)) {
                    e.preventDefault();
                    filterIcon.focus();
                }
            });
        });
    }

    initFilterIcon();

    document.body.addEventListener('htmx:sendError', function(e) {
        const url = htmxErrorUrl(e);
        if (!url) return;
        retryHtmx(url, e.detail.target || document.getElementById('detail'));
    });
    document.body.addEventListener('htmx:responseError', function(e) {
        const url = htmxErrorUrl(e);
        if (!url) return;
        retryHtmx(url, e.detail.target || document.getElementById('detail'));
    });
});
