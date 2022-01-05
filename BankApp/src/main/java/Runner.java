import util.ConnectionManager;

import java.sql.*;
import java.util.Scanner;

public class Runner {

    private static Scanner input;
    private static Connection connection;
    private static Statement statement;

    public static void main(String[] args) {
        String tablesSql = """
                create table if not exists users
                (
                    id      serial
                        primary key,
                    name    varchar(50) not null,
                    address varchar(255)
                );
                create table if not exists accounts
                (
                    id       serial
                        primary key,
                    user_id  integer
                        references users,
                    balance  numeric(13, 3),
                    currency varchar(10)
                );
                 create table if not exists transactions
                 (
                     id         serial
                         primary key,
                     account_id integer
                         references accounts,
                     amount     numeric(12, 3)
                 );""";
        input = new Scanner(System.in);
        try {
            connection = ConnectionManager.open();
            statement = connection.createStatement();
            statement.execute(tablesSql);
            start();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    private static void start() {
        System.out.println("""
    Hello! Here's instructions:
    1.Log in
    2.New user""");
        Integer answer = Integer.parseInt(input.nextLine());
        if (answer == 1) {logIn();}
        else if (answer == 2 ) {newUser();}
        else {
            System.out.println("Incorrect input:" + answer);
            start();}
    }

    private static void logIn() {
        System.out.println("""
    Please, enter your name or 'q' to go back.""");
        String name = input.nextLine();
        String sql = """
                SELECT * 
                FROM Users 
                WHERE name = ?
                """;
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, name);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()){
                manageUser(name);
            } else if (name.equalsIgnoreCase("q")){
                start();
            } else {
                System.out.println("There's no such an account");
                logIn();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert preparedStatement != null;
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void manageUser(String name) {

        System.out.println("""
    Hello! Here's instructions:
    1.Put in RUB
    2.Withdraw RUB
    3.Put in USD
    4.Withdraw USD
    5.Put in EUR
    6.Withdraw EUR
    7.Quit""");
        Integer answer = Integer.parseInt(input.nextLine());
        switch (answer) {
            case 1:
                makeOperation("put", "rub", name);
                break;
            case 2:
                makeOperation("withdraw", "rub", name);
                break;
            case 3:
                makeOperation("put", "usd", name);
                break;
            case 4:
                makeOperation("withdraw", "usd", name);
                break;
            case 5:
                makeOperation("put", "eur", name);
                break;
            case 6:
                makeOperation("withdraw", "eur", name);
                break;
            case 7:
                start();
                break;
            default:
                System.out.println("Incorrect input:" + answer);
        }
    }

    private static void makeOperation(String operation, String currency, String name) {
        System.out.println("Enter an amount of money. 'q' - to go back");
        String sql = """
                SELECT balance FROM (
                    SELECT users.name, a.balance, a.currency
                    FROM users
                    JOIN accounts a
                    ON users.id = a.user_id) as sel
                WHERE name = ? and currency = ?;
                """;
        String updateBalanceSql = """
                UPDATE accounts
                SET balance = ?
                WHERE user_id = (SELECT users.id FROM users WHERE name = ?) AND currency = ?;""";
        String insertTransactionSql = """
                INSERT INTO transactions (account_id, amount) 
                VALUES ((SELECT users.id FROM users WHERE name = ?), ?)""";
        PreparedStatement preparedStatement = null;
        PreparedStatement updateBalanceStatement = null;
        PreparedStatement insertTransactionStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, currency);

            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            Double balance = resultSet.getDouble(1);
            System.out.println(balance + " " + currency +
                    " on the balance. How much to " + operation + "?");
            Double amount = Double.parseDouble(input.nextLine());
            if (amount > 100000000 || amount < 0) {
                System.out.println("Amount must be less than 100.000.000. For withdraw, please, enter a positive number.");
                makeOperation(operation, currency, name);
            }
            Double newBalance = getNewBalance(operation, currency, name, balance, amount);
            updateBalanceStatement = connection.prepareStatement(updateBalanceSql);
            updateBalanceStatement.setDouble(1, newBalance);
            updateBalanceStatement.setString(2, name);
            updateBalanceStatement.setString(3, currency);
            updateBalanceStatement.executeUpdate();

            Double transactionAmount = null;
            if (operation.equals("withdraw")){
                transactionAmount = amount - amount*2;
            } else {
                transactionAmount=amount;
            }
            insertTransactionStatement = connection.prepareStatement(insertTransactionSql);
            insertTransactionStatement.setString(1, name);
            insertTransactionStatement.setDouble(2, transactionAmount);
            insertTransactionStatement.executeUpdate();

            manageUser(name);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert preparedStatement != null;
                preparedStatement.close();
                assert updateBalanceStatement != null;
                updateBalanceStatement.close();
                assert insertTransactionStatement != null;
                insertTransactionStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    private static Double getNewBalance(String operation, String currency, String name, Double balance, Double amount) {
        if (operation.equals("withdraw")){
            if (amount>balance) {
                System.out.println("Sorry, we can't borrow you money");
                makeOperation(operation, currency, name);
            }
            System.out.println("Withdrawal is successful! (" + amount + ").");
            return balance - amount;
        }else {
            if (amount+balance>2000000000){
                System.out.println("Sorry, your account's full");
                makeOperation(operation, currency, name);
            }
            System.out.println("Successfully put the money! (" + amount + ").");
            return balance + amount;
        }
    }

    private static void newUser() {
        System.out.println("""
    Please, enter your name.('q' to go back)""");
        String name = input.nextLine();
        if (name.equalsIgnoreCase("q")) start();
        if (isNameOccupied(name)){
            System.out.println("This name is already taken. Please, try another one");
            newUser();
        }
        System.out.println("""
    Please, enter your address(unnecessary - 's' to skip)""");
        String address = input.nextLine();



        String sql = """
                INSERT INTO Users (name, address)
                VALUES (?, ?)
                """;
        String accountsSql = """
                INSERT INTO accounts (user_id, balance, currency)
                VALUES (?, 0, 'rub'), (?, 0, 'usd'), (?, 0, 'eur')
                """;

        PreparedStatement preparedStatement = null;
        PreparedStatement accountsPreparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, name);
            if (address.equalsIgnoreCase("s")) {
                preparedStatement.setString(2, null);
            } else {
                preparedStatement.setString(2, address);
            }
            preparedStatement.executeUpdate();

            //immediately create 3 accounts
            ResultSet resultSet = preparedStatement.getGeneratedKeys();
            resultSet.next();
            int id = resultSet.getInt(1);
            accountsPreparedStatement = connection.prepareStatement(accountsSql);
            accountsPreparedStatement.setInt(1, id);
            accountsPreparedStatement.setInt(2, id);
            accountsPreparedStatement.setInt(3, id);
            accountsPreparedStatement.executeUpdate();
            manageUser(name);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert preparedStatement != null;
                preparedStatement.close();
                System.out.println("closed");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isNameOccupied(String name) {
        String sql = """
                SELECT * 
                FROM Users 
                WHERE name = ?
                """;
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, name);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()){
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert preparedStatement != null;
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
