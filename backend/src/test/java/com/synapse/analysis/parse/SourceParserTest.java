package com.synapse.analysis.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.synapse.analysis.parse.FileModel.SymbolKind;

class SourceParserTest {

    private final SourceParser parser = new SourceParser();

    private static List<String> names(FileModel m, SymbolKind kind) {
        return m.symbols().stream().filter(s -> s.kind() == kind).map(FileModel.Symbol::name).toList();
    }

    private static List<String> callees(FileModel m) {
        return m.calls().stream().map(FileModel.Call::callee).toList();
    }

    @Test
    void java() {
        String src = """
            package com.shop.order;
            import java.util.List;
            import com.shop.payment.PaymentGateway;
            // Ünïcödé comment — before identifiers to check byte offsets ✓
            @RestController
            @RequestMapping("/api/orders")
            public class OrderService extends BaseService implements Auditable, Serializable {
                private final PaymentGateway gateway;
                public OrderService(PaymentGateway gateway) { this.gateway = gateway; }
                @PostMapping("/checkout")
                public Receipt checkout(Order order) {
                    validate(order);
                    return gateway.charge(order.total(), new Receipt());
                }
                private void validate(Order o) { }
            }
            """;
        FileModel m = parser.parse("src/OrderService.java", Language.JAVA, src);
        assertThat(m.parsedWithGrammar()).isTrue();
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("OrderService");
        assertThat(names(m, SymbolKind.METHOD)).containsExactly("OrderService", "checkout", "validate");
        FileModel.Symbol cls = m.symbols().get(0);
        assertThat(cls.extendsNames()).containsExactly("BaseService");
        assertThat(cls.implementsNames()).containsExactly("Auditable", "Serializable");
        assertThat(callees(m)).contains("validate", "charge", "total", "Receipt");
        assertThat(m.imports()).containsExactly("java.util.List", "com.shop.payment.PaymentGateway");
        assertThat(m.endpoints()).extracting(FileModel.Endpoint::method, FileModel.Endpoint::path)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("POST", "/api/orders/checkout"));
        int checkoutLine = m.symbols().stream().filter(s -> s.name().equals("checkout")).findFirst().orElseThrow().startLine();
        assertThat(m.ownerAtLine(m.endpoints().get(0).line() + 1)).isEqualTo(m.ownerAtLine(checkoutLine));
    }

    @Test
    void python() {
        String src = """
            from .models import User
            import os, json
            class UserRepo(BaseRepo, metaclass=ABCMeta):
                def __init__(self, db):
                    self.db = db
                def find(self, uid):
                    return self.db.query(User).get(uid)
            @app.route("/users/<id>", methods=["GET", "DELETE"])
            def get_user(id):
                repo = UserRepo(connect())
                return json.dumps(repo.find(id))
            """;
        FileModel m = parser.parse("app/views.py", Language.PYTHON, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("UserRepo");
        assertThat(names(m, SymbolKind.METHOD)).containsExactly("__init__", "find");
        assertThat(names(m, SymbolKind.FUNCTION)).containsExactly("get_user");
        assertThat(m.symbols().get(0).extendsNames()).containsExactly("BaseRepo");
        assertThat(callees(m)).contains("query", "get", "UserRepo", "connect", "dumps", "find");
        assertThat(m.imports()).containsExactly(".models", "os", "json");
        assertThat(m.endpoints()).hasSize(2);
    }

    @Test
    void typescript() {
        String src = """
            import { Router } from 'express';
            import api from './api/client';
            export interface Repo { find(id: string): User }
            export class UserController extends Base implements Repo {
              find(id: string): User { return api.get(id); }
            }
            export const loadUsers = async () => { const c = new UserController(); return c.find('1'); };
            function helper() { return loadUsers(); }
            router.get('/users', helper);
            """;
        FileModel m = parser.parse("src/users.ts", Language.TYPESCRIPT, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("Repo", "UserController");
        assertThat(names(m, SymbolKind.FUNCTION)).containsExactly("loadUsers", "helper");
        assertThat(names(m, SymbolKind.METHOD)).contains("find");
        assertThat(callees(m)).contains("get", "UserController", "find", "loadUsers");
        assertThat(m.imports()).containsExactly("express", "./api/client");
        assertThat(m.endpoints()).hasSize(1);
    }

    @Test
    void javascriptJsx() {
        String src = """
            import React, { useState } from 'react';
            const Button = ({ label }) => <button onClick={() => track(label)}>{label}</button>;
            export default function App() { const [n] = useState(0); return <Button label="x" />; }
            """;
        FileModel m = parser.parse("src/App.jsx", Language.JAVASCRIPT, src);
        assertThat(names(m, SymbolKind.FUNCTION)).containsExactly("Button", "App");
        assertThat(callees(m)).contains("track", "useState");
    }

    @Test
    void go() {
        String src = """
            package store
            import (
                "fmt"
                "github.com/acme/shop/internal/db"
            )
            type Store struct { conn *db.Conn }
            type Reader interface { Read() error }
            func (s *Store) Save(item Item) error { return s.conn.Exec(fmt.Sprintf("x")) }
            func New() *Store { return &Store{} }
            """;
        FileModel m = parser.parse("store/store.go", Language.GO, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("Store", "Reader");
        assertThat(names(m, SymbolKind.METHOD)).containsExactly("Save");
        assertThat(m.symbols().stream().filter(s -> s.name().equals("Save")).findFirst().orElseThrow().parentName()).isEqualTo("Store");
        assertThat(names(m, SymbolKind.FUNCTION)).containsExactly("New");
        assertThat(callees(m)).contains("Exec", "Sprintf");
        assertThat(m.imports()).containsExactly("fmt", "github.com/acme/shop/internal/db");
    }

    @Test
    void cpp() {
        String src = """
            #include "engine/physics.h"
            #include <vector>
            class Body : public Entity, private Noncopyable {
            public:
              void step(double dt) { integrate(dt); }
            };
            int Body::mass() const { return compute_mass(); }
            int main() { Body b; b.step(0.1); return 0; }
            """;
        FileModel m = parser.parse("src/body.cpp", Language.CPP, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("Body");
        assertThat(m.symbols().get(0).extendsNames()).containsExactly("Entity", "Noncopyable");
        assertThat(m.symbols().stream().map(FileModel.Symbol::name)).contains("step", "mass", "main");
        assertThat(callees(m)).contains("integrate", "compute_mass", "step");
        assertThat(m.imports()).containsExactly("engine/physics.h", "vector");
    }

    @Test
    void csharp() {
        String src = """
            using System.Linq;
            using Shop.Data;
            [Route("api/[controller]")]
            public class OrdersController : ControllerBase, IOrders {
                [HttpGet("{id}")]
                public Order Get(int id) => _repo.Find(id);
            }
            """;
        FileModel m = parser.parse("Controllers/OrdersController.cs", Language.CSHARP, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("OrdersController");
        assertThat(m.symbols().get(0).extendsNames()).containsExactly("ControllerBase");
        assertThat(m.symbols().get(0).implementsNames()).containsExactly("IOrders");
        assertThat(names(m, SymbolKind.METHOD)).containsExactly("Get");
        assertThat(callees(m)).contains("Find");
        assertThat(m.endpoints()).hasSize(1);
    }

    @Test
    void phpRubyRustKotlin() {
        FileModel php = parser.parse("app/User.php", Language.PHP, """
            <?php
            namespace App;
            use App\\Models\\Post;
            class User extends Model implements Authenticatable {
                public function posts() { return $this->hasMany(Post::class); }
            }
            function helper() { return strtoupper('x'); }
            """);
        assertThat(names(php, SymbolKind.CLASS)).containsExactly("User");
        assertThat(names(php, SymbolKind.METHOD)).containsExactly("posts");
        assertThat(callees(php)).contains("hasMany", "strtoupper");

        FileModel ruby = parser.parse("app/models/user.rb", Language.RUBY, """
            require 'json'
            class User < ApplicationRecord
              def full_name
                format_name(first, last)
              end
            end
            """);
        assertThat(names(ruby, SymbolKind.CLASS)).containsExactly("User");
        assertThat(ruby.symbols().get(0).extendsNames()).containsExactly("ApplicationRecord");
        assertThat(names(ruby, SymbolKind.METHOD)).containsExactly("full_name");
        assertThat(callees(ruby)).contains("format_name");

        FileModel rust = parser.parse("src/lib.rs", Language.RUST, """
            use crate::db::Pool;
            pub struct Cache { pool: Pool }
            pub trait Store { fn get(&self) -> u32; }
            impl Store for Cache { fn get(&self) -> u32 { self.pool.fetch() } }
            pub fn build() -> Cache { Cache { pool: Pool::new() } }
            """);
        assertThat(names(rust, SymbolKind.CLASS)).containsExactly("Cache", "Store");
        assertThat(rust.symbols().stream().filter(s -> s.parentName() != null).map(FileModel.Symbol::parentName)).containsExactly("Cache");
        assertThat(rust.relations()).extracting(FileModel.Relation::toName).containsExactly("Store");
        assertThat(callees(rust)).contains("fetch", "new");

        FileModel kotlin = parser.parse("src/Main.kt", Language.KOTLIN, """
            import com.acme.Repo
            class Service(private val repo: Repo) : Base(), Runnable {
                fun run() { repo.load() }
            }
            fun main() { Service(Repo()).run() }
            """);
        assertThat(names(kotlin, SymbolKind.CLASS)).containsExactly("Service");
        assertThat(kotlin.symbols().stream().map(FileModel.Symbol::name)).contains("run", "main");
        assertThat(callees(kotlin)).contains("load", "run");
    }

    @Test
    void cobolHeuristic() {
        String src = """
                   IDENTIFICATION DIVISION.
                   PROGRAM-ID. PAYROLL.
                   PROCEDURE DIVISION.
                   MAIN-LOGIC.
                       PERFORM CALC-TAX
                       CALL 'AUDITLOG'
                       STOP RUN.
                   CALC-TAX.
                       COMPUTE TAX = GROSS * 0.2.
            """;
        FileModel m = parser.parse("cobol/PAYROLL.cbl", Language.COBOL, src);
        assertThat(names(m, SymbolKind.CLASS)).containsExactly("PAYROLL");
        assertThat(m.symbols().stream().map(FileModel.Symbol::name)).contains("MAIN-LOGIC", "CALC-TAX");
        assertThat(callees(m)).contains("CALC-TAX", "AUDITLOG");
    }

    @Test
    void scalaHeuristic() {
        String src = """
            import scala.util.Try
            class Ledger(accounts: Seq[Account]) {
              def balance(id: String): BigDecimal = accounts.find(_.id == id).map(sum).getOrElse(0)
            }
            object Main {
              def main(args: Array[String]): Unit = new Ledger(Nil).balance("x")
            }
            """;
        FileModel m = parser.parse("src/Ledger.scala", Language.SCALA, src);
        assertThat(names(m, SymbolKind.CLASS)).contains("Ledger", "Main");
        assertThat(m.symbols().stream().map(FileModel.Symbol::name)).contains("balance", "main");
        assertThat(m.imports()).containsExactly("scala.util.Try");
    }
}
