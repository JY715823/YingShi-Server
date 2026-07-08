-- V21: Ledger relational tables (replaces single JSON snapshot)

create table if not exists ledger_books (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    name varchar(255) not null,
    creator_user_id varchar(255),
    template varchar(255) not null,
    currency_code varchar(20) not null,
    currency_symbol varchar(20) not null,
    cover_color bigint not null,
    sort_order integer not null,
    is_deleted boolean not null default false,
    constraint ledger_books_pkey primary key (id)
);

create index if not exists idx_ledger_books_library_id
    on ledger_books (library_id);

create table if not exists ledger_categories (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    name varchar(255) not null,
    icon_key varchar(255) not null,
    color bigint not null,
    type varchar(20) not null,
    sort_order integer not null,
    hidden boolean not null default false,
    constraint ledger_categories_pkey primary key (id),
    constraint ledger_categories_type_check check (type in ('EXPENSE', 'INCOME'))
);

create index if not exists idx_ledger_categories_library_id
    on ledger_categories (library_id);

create index if not exists idx_ledger_categories_book_id
    on ledger_categories (book_id);

create table if not exists ledger_accounts (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    name varchar(255) not null,
    type varchar(20) not null,
    icon_key varchar(255) not null,
    color bigint not null,
    initial_balance_cents bigint not null,
    balance_cents bigint not null,
    credit_limit_cents bigint,
    include_in_total boolean not null default true,
    hidden boolean not null default false,
    note text,
    sort_order integer not null,
    constraint ledger_accounts_pkey primary key (id),
    constraint ledger_accounts_type_check check (type in ('CASH', 'DEBIT_CARD', 'CREDIT', 'ALIPAY', 'WECHAT', 'INVESTMENT', 'DEBT', 'OTHER'))
);

create index if not exists idx_ledger_accounts_library_id
    on ledger_accounts (library_id);

create index if not exists idx_ledger_accounts_book_id
    on ledger_accounts (book_id);

create table if not exists ledger_transactions (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    category_id varchar(255),
    account_id varchar(255) not null,
    to_account_id varchar(255),
    amount_cents bigint not null,
    type varchar(20) not null,
    occurred_at_millis bigint not null,
    remark text,
    method varchar(255),
    deleted_at_millis bigint,
    constraint ledger_transactions_pkey primary key (id),
    constraint ledger_transactions_type_check check (type in ('EXPENSE', 'INCOME', 'TRANSFER'))
);

create index if not exists idx_ledger_transactions_library_id
    on ledger_transactions (library_id);

create index if not exists idx_ledger_transactions_book_id
    on ledger_transactions (book_id);

create index if not exists idx_ledger_transactions_account_id
    on ledger_transactions (account_id);

create index if not exists idx_ledger_transactions_category_id
    on ledger_transactions (category_id);

create index if not exists idx_ledger_transactions_occurred_at
    on ledger_transactions (library_id, occurred_at_millis desc);

create index if not exists idx_ledger_transactions_library_updated
    on ledger_transactions (library_id, updated_at);

create table if not exists ledger_budgets (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    period varchar(20) not null,
    start_millis bigint not null,
    end_millis bigint not null,
    total_amount_cents bigint not null,
    constraint ledger_budgets_pkey primary key (id),
    constraint ledger_budgets_period_check check (period in ('WEEK', 'MONTH', 'QUARTER', 'YEAR'))
);

create index if not exists idx_ledger_budgets_library_id
    on ledger_budgets (library_id);

create index if not exists idx_ledger_budgets_book_id
    on ledger_budgets (book_id);

create table if not exists ledger_category_budgets (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    budget_id varchar(255) not null,
    category_id varchar(255) not null,
    amount_cents bigint not null,
    constraint ledger_category_budgets_pkey primary key (id)
);

create index if not exists idx_ledger_category_budgets_library_id
    on ledger_category_budgets (library_id);

create index if not exists idx_ledger_category_budgets_budget_id
    on ledger_category_budgets (budget_id);

create table if not exists ledger_deleted_items (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    item_id varchar(255) not null,
    type varchar(20) not null,
    title varchar(255) not null,
    amount_cents bigint not null,
    deleted_at_millis bigint not null,
    expires_at_millis bigint not null,
    constraint ledger_deleted_items_pkey primary key (id),
    constraint ledger_deleted_items_type_check check (type in ('TRANSACTION'))
);

create index if not exists idx_ledger_deleted_items_library_id
    on ledger_deleted_items (library_id);

create index if not exists idx_ledger_deleted_items_book_id
    on ledger_deleted_items (book_id);

create table if not exists ledger_recurring_rules (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    book_id varchar(255) not null,
    type varchar(20) not null,
    category_id varchar(255),
    account_id varchar(255) not null,
    to_account_id varchar(255),
    amount_cents bigint not null,
    remark text,
    frequency varchar(20) not null,
    start_at_millis bigint not null,
    end_at_millis bigint,
    next_occurrence_at_millis bigint not null,
    enabled boolean not null default true,
    constraint ledger_recurring_rules_pkey primary key (id),
    constraint ledger_recurring_rules_type_check check (type in ('EXPENSE', 'INCOME', 'TRANSFER')),
    constraint ledger_recurring_rules_frequency_check check (frequency in ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'))
);

create index if not exists idx_ledger_recurring_rules_library_id
    on ledger_recurring_rules (library_id);

create index if not exists idx_ledger_recurring_rules_book_id
    on ledger_recurring_rules (book_id);

create table if not exists ledger_recurring_occurrences (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    rule_id varchar(255) not null,
    transaction_id varchar(255) not null,
    occurrence_at_millis bigint not null,
    constraint ledger_recurring_occurrences_pkey primary key (id)
);

create index if not exists idx_ledger_recurring_occurrences_library_id
    on ledger_recurring_occurrences (library_id);

create index if not exists idx_ledger_recurring_occurrences_rule_id
    on ledger_recurring_occurrences (rule_id);

create index if not exists idx_ledger_recurring_occurrences_transaction_id
    on ledger_recurring_occurrences (transaction_id);
