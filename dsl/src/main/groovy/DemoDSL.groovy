/**
 * Created by jzj on 17/7/21.
 */
class Utils {

    /**
     * 指定代理对象,运行闭包
     */
    static void runClosureAgainstObject(Closure closure, Object delegate) {
        Closure c = (Closure) closure.clone()
        c.delegate = delegate
        c.call()
    }

    /**
     * 用闭包配置Object
     */
    static void configureObjectWithClosure(Object object, Closure closure) {
        Closure c = (Closure) closure.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST;
        c.delegate = object
        c.call()
    }

    /**
     * 加载Groovy类并创建实例
     */
    static GroovyObject loadAndCreateGroovyObject(File sourceFile) {
        Class groovyClass = new GroovyClassLoader().parseClass(sourceFile);
        return (GroovyObject) groovyClass.newInstance();
    }

    /**
     * 给GroovyObject设置代理对象
     */
    static void setDelegateForGroovyObject(GroovyObject obj, Object delegate) {
        obj.metaClass.getProperty = { String name ->
            def metaProperty = obj.metaClass.getMetaProperty(name)
            metaProperty != null ? metaProperty : delegate.getProperty(name)
        }
        obj.metaClass.invokeMethod = { String name, Object[] args ->
            def metaMethod = obj.metaClass.getMetaMethod(name, args)
            metaMethod != null ? metaMethod.invoke(obj, args) : delegate.invokeMethod(name, args)
        }
    }
}

class Project {

    ConfigurationContainer configurations = new ConfigurationContainer()
    DependencyHandler dependencies = new DependencyHandler(this)
    Project project = this

    void configurations(Closure closure) {
        Utils.runClosureAgainstObject(closure, configurations)
    }

    void dependencies(Closure closure) {
        Utils.runClosureAgainstObject(closure, dependencies)
    }
}

class Configuration {

    List<Dependency> dependencies = new ArrayList<>()
}

class ConfigurationContainer {

    Map<String, Configuration> configurations = new HashMap<>()

    Object propertyMissing(String name) {
        println "add configuration '$name'"
        configurations.put(name, new Configuration())
    }
}

class Dependency {

    String name
    boolean transitive = true

    Dependency(String name) {
        this.name = name
    }

    @Override
    String toString() {
        return "[${name}, transitive = ${transitive}]"
    }
}

class DependencyHandler {

    Project project;

    DependencyHandler(Project project) {
        this.project = project;
    }

    void add(String configuration, String dependencyNotation) {
        add(configuration, new Dependency(dependencyNotation), null)
    }

    void add(String configuration, String dependencyNotation, Closure closure) {
        add(configuration, new Dependency(dependencyNotation), closure)
    }

    void add(String configuration, Dependency dependency) {
        add(configuration, dependency, null)
    }

    void add(String configuration, Dependency dependency, Closure closure) {
        Configuration cfg = this.project.configurations.configurations.get(configuration)
        if (cfg != null) {
            if (closure != null) {
                Utils.configureObjectWithClosure(dependency, closure)
            }
            cfg.dependencies.add(dependency)
            println "add dependency '${dependency}' to '${configuration}'"
        } else {
            println "configuration '${configuration}' not found, dependency is '${dependency}'"
        }
    }

    Dependency project(Map<String, ?> notation) {
        return new Dependency("project(${notation.get("path")})")
    }

    Object methodMissing(String name, Object args) {
        Object[] arr = (Object[]) args;
        if (arr.length >= 1 && (arr[0] instanceof String || arr[0] instanceof Dependency)
                && this.project.configurations.configurations.get(name) != null) {
            Dependency dependency = arr[0] instanceof String ? new Dependency((String) arr[0]) : (Dependency) arr[0];
            if (arr.length == 1) {
                add(name, dependency)
            } else if (arr.length == 2 && arr[1] instanceof Closure) {
                add(name, dependency, (Closure) arr[1])
            }
        } else {
            println "method '${name}' with args '${args}' not found!"
        }
        return null
    }
}

// 创建Project对象
def project = new Project()
// 加载并实例化Groovy对象
def groovyObject = Utils.loadAndCreateGroovyObject(new File('./myBuild.gradle'))
// 给groovyObject设置代理对象
Utils.setDelegateForGroovyObject(groovyObject, project)
// 执行脚本(Execute "build.gradle" file against the project)
groovyObject.invokeMethod("run", null)

