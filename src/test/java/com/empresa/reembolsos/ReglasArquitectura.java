package com.empresa.reembolsos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.empresa.reembolsos.procesamiento.calculo.CalculadoraReembolso;
import com.empresa.reembolsos.procesamiento.notificacion.ObservadorCambioEstado;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura que verifican los limites que si existen en el sistema
 * (ninguna asume puertos de persistencia, coherente con ADR-001).
 */
// DoNotIncludeTests: las pruebas unitarias viven en los mismos paquetes que el codigo de produccion
// (p. ej. CalculadoraViajeTest en procesamiento.calculo, NotificadoresTest en procesamiento.notificacion).
// Si se importaran, violarian las Reglas 3 y 4 sin que el codigo de produccion tenga ningun problema.
@AnalyzeClasses(packages = "com.empresa.reembolsos", importOptions = ImportOption.DoNotIncludeTests.class)
public class ReglasArquitectura {

    // Regla 1: procesamiento no depende de infraestructura, adaptadores, JPA ni de la capa web.
    // Permite los estereotipos de Spring (@Component, @Order): ver Decision de diseno 4 en el README.
    // Se agregan org.springframework.web/http respecto al enunciado para que la regla cumpla su nombre
    // ("NiWeb"): un cliente HTTP (p. ej. hacia la API de Contabilidad) debe vivir en infraestructura.
    @ArchTest
    static final ArchRule procesamientoSinPersistenciaNiWeb = noClasses()
        .that().resideInAPackage("..procesamiento..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "..infraestructura..", "..adaptadores..",
            "org.springframework.data..", "jakarta.persistence..",
            "org.springframework.web..", "org.springframework.http..");

    // Regla 2: el controlador solo accede al servicio de aplicacion y al dominio.
    // Se agrega "..adaptadores.rest.." respecto al enunciado: sin el, la regla marca como violacion que
    // el controlador lea su propio campo 'servicio' y que el advice llame a su propio metodo privado.
    @ArchTest
    static final ArchRule controladorSoloUsaServicio = classes()
        .that().resideInAPackage("..adaptadores.rest..")
        .should().onlyAccessClassesThat()
        .resideInAnyPackage(
            "..adaptadores.rest..", "..aplicacion..", "..dominio..",
            "org.springframework.web..", "org.springframework.http..", "java..");

    // Regla 3: las calculadoras implementan CalculadoraReembolso.
    // Se acota a las clases cuyo nombre empieza por "Calculadora": SelectorCalculadora tambien vive en
    // procesamiento.calculo, pero no es una estrategia sino quien la elige, y no debe implementarla.
    @ArchTest
    static final ArchRule calculadorasImplementanInterfaz = classes()
        .that().resideInAPackage("..procesamiento.calculo..")
        .and().areNotInterfaces()
        .and().haveSimpleNameStartingWith("Calculadora")
        .should().implement(CalculadoraReembolso.class);

    // Regla 4: los notificadores implementan ObservadorCambioEstado
    @ArchTest
    static final ArchRule notificadoresImplementanInterfaz = classes()
        .that().resideInAPackage("..procesamiento.notificacion..")
        .and().areNotInterfaces()
        .should().implement(ObservadorCambioEstado.class);
}
