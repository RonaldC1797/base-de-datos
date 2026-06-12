package com.driver.main;

import com.driver.config.MySQLConfig;
import com.driver.config.RedisConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redis.clients.jedis.Jedis;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class MainService {
    
    private static final String CACHE_KEY_TOTAL = "employees:total";
    private static int ejecucion = 0;
    
    public static void main(String[] args) {
        
        try (Jedis jedis = RedisConfig.getConnection();
             Scanner scanner = new Scanner(System.in)) {
            
            jedis.ping();
            System.out.println("✅ Conectado a Redis\n");
            
            while (true) {
                System.out.println("\n1. Consultar TOTAL de empleados (salario > 800)");
                System.out.println("2. Limpiar cache");
                System.out.println("3. Salir");
                System.out.print("Opción: ");
                
                String opcion = scanner.nextLine().trim();
                
                if (opcion.equals("1")) {
                    consultarTotal(jedis);
                } else if (opcion.equals("2")) {
                    jedis.del(CACHE_KEY_TOTAL);
                    System.out.println("✅ Cache limpiado");
                } else if (opcion.equals("3")) {
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    // ============================================================
    // CONSULTAR TOTAL DE EMPLEADOS CON SALARIO > 800
    // SIN LIBERAR CACHE EN NINGUNA EJECUCIÓN
    // ============================================================
    private static void consultarTotal(Jedis jedis) {
        ejecucion++;
        System.out.println("\n--- CONSULTA TOTAL (salario > 800) - EJECUCIÓN #" + ejecucion + " ---");
        
        long startTime = System.nanoTime();
        
        String cacheData = jedis.get(CACHE_KEY_TOTAL);
        int totalRegistros = 0;
        
        if (cacheData != null) {
            // Viene de Redis Cache
            JsonObject json = JsonParser.parseString(cacheData).getAsJsonObject();
            totalRegistros = json.get("total").getAsInt();
            System.out.println("Origen: REDIS CACHE");
        } else {
            // No está en cache, consultar MySQL
            totalRegistros = contarMySQL();
            
            // Guardar en Redis siempre (sin importar la ejecución)
            JsonObject json = new JsonObject();
            json.addProperty("total", totalRegistros);
            json.addProperty("hora", System.currentTimeMillis());
            jedis.set(CACHE_KEY_TOTAL, json.toString());
            jedis.expire(CACHE_KEY_TOTAL, 3600);
            System.out.println("Origen: MYSQL (guardado en cache)");
        }
        
        long endTime = System.nanoTime();
        long tiempoMs = (endTime - startTime) / 1_000_000;
        double tiempoSeg = tiempoMs / 1000.0;
        
        System.out.println("Total registros (salario > 800): " + String.format("%,d", totalRegistros));
        System.out.println("\n⏱️ TIEMPOS:");
        System.out.println("   Milisegundos: " + tiempoMs + " ms");
        System.out.println("   Segundos: " + String.format("%.3f", tiempoSeg) + " seg");
        
        System.out.println("\n📝 ANOTE EN LA TABLA:");
        System.out.println("   Ejecución #" + ejecucion);
        System.out.println("   Tiempo Driver: " + tiempoMs + " ms  |  " + String.format("%.3f", tiempoSeg) + " seg");
    }
    
    // ============================================================
    // CONTAR TOTAL REGISTROS EN MYSQL (salario > 800)
    // ============================================================
    private static int contarMySQL() {
        String query = "SELECT COUNT(*) as total FROM employees e " +
                       "JOIN salaries s ON e.emp_no = s.emp_no " +
                       "JOIN dept_emp de ON e.emp_no = de.emp_no " +
                       "JOIN departments d ON de.dept_no = d.dept_no " +
                       "WHERE s.salary > 800";
        
        try (Connection conn = MySQLConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt("total");
            }
            
        } catch (Exception e) {
            System.err.println("Error MySQL: " + e.getMessage());
        }
        return 0;
    }
}