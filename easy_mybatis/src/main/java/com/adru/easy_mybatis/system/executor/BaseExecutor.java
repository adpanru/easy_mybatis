package com.adru.easy_mybatis.system.executor;

import com.adru.easy_mybatis.system.Configuration;
import com.adru.easy_mybatis.system.MappedStatement;
import com.adru.easy_mybatis.system.SimpleCatch;
import com.adru.easy_mybatis.system.utils.UnderlineAndHumpUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xuwei
 */
public class BaseExecutor implements Executor {

    @Override
    public List<Object> queryList(Connection connection, MappedStatement mappedStatement, Object[] args, Configuration configuration) throws ClassNotFoundException, SQLException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        if (mappedStatement.getResultType() == null) {
            System.out.println("未定义resultType!");
            return null;
        }
        PreparedStatement preparedStatement = initPreparedStatement(connection, mappedStatement, args);
        if (configuration.isOpenTheCatch()){
            SimpleCatch simpleCatch = configuration.getSimpleCatch();
            if (simpleCatch.containsKey(preparedStatement.toString())){
                return resultToBean(mappedStatement.getResultType(),simpleCatch.get(preparedStatement.toString()),configuration.isUnderlineAndHump());
            }else {
                ResultSet resultSet = preparedStatement.executeQuery();
                simpleCatch.put(preparedStatement.toString(),resultSet);
                return resultToBean(mappedStatement.getResultType(), resultSet, configuration.isUnderlineAndHump());
            }
        }
        return resultToBean(mappedStatement.getResultType(), preparedStatement.executeQuery(), configuration.isUnderlineAndHump());
    }

    @Override
    public Object queryOne(Connection connection, MappedStatement mappedStatement, Object[] args, Configuration configuration) throws SQLException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        if (mappedStatement.getResultType() == null) {
            System.out.println("未定义resultType!");
            return null;
        }
        PreparedStatement preparedStatement = initPreparedStatement(connection, mappedStatement, args);
        if (configuration.isOpenTheCatch()){
            SimpleCatch simpleCatch = configuration.getSimpleCatch();
            if (simpleCatch.containsKey(preparedStatement.toString())){
                List<Object> objectList = resultToBean(mappedStatement.getResultType(), simpleCatch.get(preparedStatement.toString()), configuration.isUnderlineAndHump());
                return objectList.size() == 0 ? null : objectList.get(0);
            }else {
                ResultSet resultSet = preparedStatement.executeQuery();
                simpleCatch.put(preparedStatement.toString(),resultSet);
                List<Object> objectList = resultToBean(mappedStatement.getResultType(), preparedStatement.executeQuery(), configuration.isUnderlineAndHump());
                return objectList.size() == 0 ? null : objectList.get(0);
            }
        }
        List<Object> objectList = resultToBean(mappedStatement.getResultType(), preparedStatement.executeQuery(), configuration.isUnderlineAndHump());
        return objectList.size() == 0 ? null : objectList.get(0);
    }

    @Override
    public int update(Connection connection, MappedStatement mappedStatement, Object[] args, Configuration configuration) throws SQLException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if (configuration.isOpenTheCatch()){
            configuration.getSimpleCatch().clear();
        }
        return initPreparedStatement(connection, mappedStatement, args).executeUpdate();
    }

    public PreparedStatement initPreparedStatement(Connection connection, MappedStatement mappedStatement, Object[] args) throws SQLException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String regex = "\\{([^}]*)}";
        Pattern pattern = Pattern.compile(regex);
        String sql = mappedStatement.getSql();
        Matcher matcher = pattern.matcher(sql);
        List<String> params = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        //处理sql为预编译形式
        if (mappedStatement.getSql().contains("#{")) {
            if (mappedStatement.getParameterType() == null) {
                //如果采用预编译形式却未定义parameterType则报错
                System.out.println("未定义parameterType!");
                return null;
            }
            while (matcher.find()) {
                String param = matcher.group(1);
                params.add(param);
                sql = sql.replace("#{" + param + "}", "?");
            }
            preparedStatement = connection.prepareStatement(sql);
            switch (mappedStatement.getParameterType()) {
                case "int":
                case "java.lang.Integer":
                    int num = Integer.parseInt(args[0].toString());
                    preparedStatement.setInt(1, num);
                    break;
                case "java.lang.String":
                    String str = args[0].toString();
                    preparedStatement.setString(1, str);
                    break;
                default:
                    Class clazz = Class.forName(mappedStatement.getParameterType());
                    Object obj = args[0];
                    String name = "";
                    for (int i = 0; i < params.size(); i++) {
                        name = params.get(i).substring(0, 1).toUpperCase() + params.get(i).substring(1);
                        String methodName = "get" + name;
                        Method methodObj = clazz.getMethod(methodName);
                        //调用getter方法完成赋值
                        String value = methodObj.invoke(obj).toString();
                        preparedStatement.setString(i + 1, value);
                    }
                    break;
            }
        } else {
            preparedStatement = connection.prepareStatement(sql);
        }
        return preparedStatement;
    }

    public List<Object> resultToBean(String resultType, ResultSet resultSet, boolean underlineAndHump) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, SQLException, InstantiationException, ClassNotFoundException {
        Object obj = null;
        List<Object> objectList = new ArrayList<>();
        while (resultSet.next()) {
            //反射创建对象
            Class clazz = Class.forName(resultType);
            obj = clazz.newInstance();
            //获取ResultSet数据
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            //遍历实体类属性集合，依次将结果集中的值赋给属性
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Object value = setFieldValueByResultSet(fields[i], resultSetMetaData, resultSet, underlineAndHump);
                //通过属性名找到对应的setter方法
                String name = fields[i].getName();
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
                String methodName = "set" + name;
                Method methodObj = clazz.getMethod(methodName, fields[i].getType());
                //调用setter方法完成赋值
                methodObj.invoke(obj, value);
            }
            objectList.add(obj);
        }
        return objectList;
    }


    public Object setFieldValueByResultSet(Field field, ResultSetMetaData rsmd, ResultSet rs, boolean underlineAndHump) {
        Object result = null;
        try {
            int count = rsmd.getColumnCount();
            for (int i = 1; i <= count; i++) {
                if (field.getName().equals(underlineAndHump ? UnderlineAndHumpUtil.underlineToHump(rsmd.getColumnName(i)) : rsmd.getColumnName(i))) {
                    String type = field.getType().getName();
                    switch (type) {
                        case "int":
                        case "java.lang.Integer":
                            result = rs.getInt(underlineAndHump ? UnderlineAndHumpUtil.humpToUnderline(field.getName()) : field.getName());
                            break;
                        case "java.lang.String":
                            result = rs.getString(underlineAndHump ? UnderlineAndHumpUtil.humpToUnderline(field.getName()) : field.getName());
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return result;
    }
}