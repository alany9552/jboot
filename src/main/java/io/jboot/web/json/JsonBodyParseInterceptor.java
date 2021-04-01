/**
 * Copyright (c) 2015-2021, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jboot.web.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jfinal.aop.Interceptor;
import com.jfinal.aop.Invocation;
import com.jfinal.core.ActionException;
import com.jfinal.kit.LogKit;
import com.jfinal.render.RenderManager;
import io.jboot.aop.InterceptorBuilder;
import io.jboot.aop.Interceptors;
import io.jboot.aop.annotation.AutoLoad;
import io.jboot.utils.ClassUtil;
import io.jboot.utils.DateUtil;
import io.jboot.utils.StrUtil;
import io.jboot.web.controller.JbootController;
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl;

import java.lang.reflect.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@AutoLoad
public class JsonBodyParseInterceptor implements Interceptor, InterceptorBuilder {

    private static final String startOfArray = "[";
    private static final String endOfArray = "]";

    @Override
    public void intercept(Invocation inv) {
        String rawData = inv.getController().getRawData();
        if (StrUtil.isBlank(rawData)) {
            inv.invoke();
            return;
        }

        Method method = inv.getMethod();
        Parameter[] parameters = method.getParameters();
        Type[] paraTypes = method.getGenericParameterTypes();

        Object jsonObjectOrArray = JSON.parse(rawData);

        for (int index = 0; index < parameters.length; index++) {
            JsonBody jsonBody = parameters[index].getAnnotation(JsonBody.class);
            if (jsonBody != null) {
                Class<?> paraClass = parameters[index].getType();
                Object result = null;
                try {
                    Type paraType = paraTypes[index];
                    if (paraType instanceof TypeVariable) {
                        Type variableRawType = getVariableRawType(inv.getController().getClass(), ((TypeVariableImpl<?>) paraType));
                        if (variableRawType != null) {
                            paraClass = (Class<?>) variableRawType;
                            paraType = variableRawType;
                        }
                    }
                    result = parseJsonBody(jsonObjectOrArray, paraClass, paraType, jsonBody.value());
                } catch (Exception e) {
                    String message = "Can not parse \"" + parameters[index].getType()
                            + "\" in method " + ClassUtil.buildMethodString(method) + ", Cause: " + e.getMessage();
                    if (jsonBody.skipConvertError()) {
                        LogKit.error(message);
                    } else {
                        throw new ActionException(400, RenderManager.me().getRenderFactory().getErrorRender(400), message);
                    }
                }

                inv.setArg(index, result);
            }
        }

        inv.invoke();
    }


    /**
     * 获取方法里的泛型参数 T 对于的真实的 Class 类
     *
     * @param defClass
     * @param typeVariable
     * @return
     */
    private static Type getVariableRawType(Class<?> defClass, TypeVariable typeVariable) {
        Type type = defClass.getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
            if (typeArguments.length == 1) {
                return typeArguments[0];
            } else if (typeArguments.length > 1) {
                TypeVariable<?>[] typeVariables = typeVariable.getGenericDeclaration().getTypeParameters();
                for (int i = 0; i < typeVariables.length; i++) {
                    if (typeVariable.getName().equals(typeVariables[i].getName())) {
                        return typeArguments[i];
                    }
                }
            }
        }
        return null;
    }


    public static Object parseJsonBody(Object jsonObjectOrArray, Class<?> paraClass, Type paraType, String jsonKey) throws InstantiationException, IllegalAccessException {
        if (jsonObjectOrArray == null) {
            return paraClass.isPrimitive() ? getPrimitiveDefaultValue(paraClass) : null;
        }
        if (Collection.class.isAssignableFrom(paraClass) || paraClass.isArray()) {
            return parseArray(jsonObjectOrArray, paraClass, paraType, jsonKey);
        } else {
            return parseObject((JSONObject) jsonObjectOrArray, paraClass, paraType, jsonKey);
        }
    }


    private static Object parseObject(JSONObject rawObject, Class<?> paraClass, Type paraType, String jsonKey) throws IllegalAccessException, InstantiationException {
        if (StrUtil.isBlank(jsonKey)) {
            return toJavaObject(rawObject, paraClass, paraType);
        }

        Object result = null;
        String[] keys = jsonKey.split("\\.");
        for (int i = 0; i < keys.length; i++) {
            if (rawObject != null && !rawObject.isEmpty()) {
                String key = keys[i].trim();
                if (StrUtil.isNotBlank(key)) {
                    //the last
                    if (i == keys.length - 1) {
                        if (key.endsWith(endOfArray) && key.contains(startOfArray)) {
                            String realKey = key.substring(0, key.indexOf(startOfArray));
                            JSONArray jarray = rawObject.getJSONArray(realKey.trim());
                            if (jarray != null && jarray.size() > 0) {
                                String arrayString = key.substring(key.indexOf(startOfArray) + 1, key.length() - 1);
                                int arrayIndex = StrUtil.isBlank(arrayString) ? 0 : Integer.parseInt(arrayString.trim());
                                result = arrayIndex >= jarray.size() ? null : jarray.get(arrayIndex);
                            }
                        } else {
                            result = rawObject.get(key);
                        }
                    }
                    //not last
                    else {
                        rawObject = getJSONObjectByKey(rawObject, key);
                    }
                }
            }
        }

        if (result == null || StrUtil.EMPTY.equals(result)) {
            return paraClass.isPrimitive() ? getPrimitiveDefaultValue(paraClass) : null;
        }

        if (result instanceof JSONObject) {
            return toJavaObject((JSONObject) result, paraClass, paraType);
        } else {
            return convert(result, paraClass);
        }
    }


    private static Object parseArray(Object rawJsonObjectOrArray, Class<?> typeClass, Type type, String jsonKey) {
        JSONArray jsonArray = null;
        if (StrUtil.isBlank(jsonKey)) {
            jsonArray = (JSONArray) rawJsonObjectOrArray;
        } else {
            JSONObject rawObject = (JSONObject) rawJsonObjectOrArray;
            String[] keys = jsonKey.split("\\.");
            for (int i = 0; i < keys.length; i++) {
                if (rawObject == null || rawObject.isEmpty()) {
                    break;
                }
                String key = keys[i].trim();
                if (StrUtil.isNotBlank(key)) {
                    //the last
                    if (i == keys.length - 1) {
                        if (key.endsWith(endOfArray) && key.contains(startOfArray)) {
                            String realKey = key.substring(0, key.indexOf(startOfArray));
                            JSONArray jarray = rawObject.getJSONArray(realKey.trim());
                            if (jarray == null || jarray.isEmpty()) {
                                return null;
                            }
                            String subKey = key.substring(key.indexOf(startOfArray) + 1, key.length() - 1).trim();
                            if (StrUtil.isBlank(subKey)) {
                                throw new IllegalStateException("Sub key can not empty: " + jsonKey);
                            }

                            JSONArray newJsonArray = new JSONArray();
                            for (int j = 0; j < jarray.size(); j++) {
                                Object value = jarray.getJSONObject(j).get(subKey);
                                if (value != null) {
                                    newJsonArray.add(value);
                                }
                            }
                            jsonArray = newJsonArray;
                        } else {
                            jsonArray = rawObject.getJSONArray(key);
                        }
                    }
                    //not last
                    else {
                        rawObject = getJSONObjectByKey(rawObject, key);
                    }
                }
            }
        }

        if (jsonArray == null || jsonArray.isEmpty()) {
            return null;
        }

        //非泛型 set
        if ((typeClass == Set.class || typeClass == HashSet.class) && typeClass == type) {
            return new HashSet<>(jsonArray);
        }

        return jsonArray.toJavaObject(type);
    }


    private static JSONObject getJSONObjectByKey(JSONObject jsonObject, String key) {
        if (key.endsWith(endOfArray) && key.contains(startOfArray)) {
            String realKey = key.substring(0, key.indexOf(startOfArray));
            JSONArray jarray = jsonObject.getJSONArray(realKey.trim());
            if (jarray == null || jarray.isEmpty()) {
                return null;
            }
            String arrayString = key.substring(key.indexOf(startOfArray) + 1, key.length() - 1);
            int arrayIndex = StrUtil.isBlank(arrayString) ? 0 : Integer.parseInt(arrayString.trim());
            return arrayIndex >= jarray.size() ? null : jarray.getJSONObject(arrayIndex);
        } else {
            return jsonObject.getJSONObject(key);
        }
    }


    private static Object toJavaObject(JSONObject rawObject, Class<?> paraClass, Type paraType) throws IllegalAccessException, InstantiationException {
        if (rawObject.isEmpty()) {
            return paraClass.isPrimitive() ? getPrimitiveDefaultValue(paraClass) : null;
        }

        //非泛型 的 map
        if ((paraClass == Map.class || paraClass == JSONObject.class) && paraClass == paraType) {
            return rawObject;
        }

        //非泛型 的 map
        if (Map.class.isAssignableFrom(paraClass) && paraClass == paraType && canNewInstance(paraClass)) {
            Map map = (Map) paraClass.newInstance();
            map.putAll(rawObject);
            return map;
        }

        return rawObject.toJavaObject(paraType);
    }


    private static boolean canNewInstance(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        return !Modifier.isAbstract(modifiers) && !Modifier.isInterface(modifiers);
    }


    private static Object convert(Object value, Class<?> targetClass) {
        if (value.getClass().isAssignableFrom(targetClass)) {
            return value;
        }

        if (targetClass == Integer.class || targetClass == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetClass == Long.class || targetClass == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetClass == Double.class || targetClass == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetClass == Float.class || targetClass == float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        } else if (targetClass == Boolean.class || targetClass == boolean.class) {
            String v = value.toString().toLowerCase();
            if ("1".equals(v) || "true".equals(v)) {
                return Boolean.TRUE;
            } else if ("0".equals(v) || "false".equals(v)) {
                return Boolean.FALSE;
            } else {
                throw new RuntimeException("Can not parse to boolean type of value: " + value);
            }
        } else if (targetClass == java.math.BigDecimal.class) {
            return new java.math.BigDecimal(value.toString());
        } else if (targetClass == java.math.BigInteger.class) {
            return new java.math.BigInteger(value.toString());
        } else if (targetClass == byte[].class) {
            return value.toString().getBytes();
        } else if (targetClass == Date.class) {
            return parseDate(value);
        } else if (targetClass == LocalDateTime.class) {
            return DateUtil.toLocalDateTime(parseDate(value));
        } else if (targetClass == LocalDate.class) {
            return DateUtil.toLocalDate(parseDate(value));
        } else if (targetClass == LocalTime.class) {
            return DateUtil.toLocalTime(parseDate(value));
        } else if (targetClass == Short.class || targetClass == short.class) {
            if (value instanceof Number) {
                return ((Number) value).shortValue();
            }
            return Short.parseShort(value.toString());
        }

        throw new RuntimeException(targetClass.getName() + " can not be parsed in json.");
    }


    private static Date parseDate(Object value) {
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        return DateUtil.parseDate(value.toString());
    }

    private static Object getPrimitiveDefaultValue(Class<?> paraClass) {
        if (paraClass == int.class || paraClass == long.class || paraClass == float.class || paraClass == double.class) {
            return 0;
        } else if (paraClass == boolean.class) {
            return Boolean.FALSE;
        } else if (paraClass == short.class) {
            return (short) 0;
        } else if (paraClass == byte.class) {
            return (byte) 0;
        } else if (paraClass == char.class) {
            return '\u0000';
        } else {
            //不存在这种类型
            return null;
        }
    }


    @Override
    public void build(Class<?> targetClass, Method method, Interceptors interceptors) {
        if (Util.isController(targetClass)) {
            Parameter[] parameters = method.getParameters();
            if (parameters != null && parameters.length > 0) {
                for (Parameter p : parameters) {
                    if (p.getAnnotation(JsonBody.class) != null) {
                        Class<?> typeClass = p.getType();
                        if ((Map.class.isAssignableFrom(typeClass) || Collection.class.isAssignableFrom(typeClass) || typeClass.isArray())
                                && !JbootController.class.isAssignableFrom(targetClass)) {
                            throw new IllegalArgumentException("Can not use @JsonBody for Map/List(Collection)/Array type if your controller not extends JbootController, method: " + ClassUtil.buildMethodString(method));
                        }

                        interceptors.addIfNotExist(this);
                        return;
                    }
                }
            }
        }
    }
}