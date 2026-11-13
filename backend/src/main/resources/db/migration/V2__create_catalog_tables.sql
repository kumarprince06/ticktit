-- V2: Movie catalog (STORAGE.md "Catalog", db-design.md "Catalog").
-- Seat LAYOUT belongs to a screen (drawn once). Seat STATUS belongs to a show (copied from the layout).
-- Money is in paise, BIGINT (ADR-009). Seat LOCKS live in Redis, never in these tables.
-- Status/category/type columns hold Java enum names as text; no CHECK lists, so new values need no migration.

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed for "no two shows overlap on one screen"

CREATE TABLE cities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    state_code VARCHAR(2) NOT NULL,                 -- GST state code, e.g. 19 = West Bengal
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_city_slug ON cities (slug);

CREATE TABLE theatres (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    area VARCHAR(100) NOT NULL,
    pincode VARCHAR(6) NOT NULL,
    lat NUMERIC(9, 6) NULL,
    lng NUMERIC(9, 6) NULL,
    gstin VARCHAR(15) NULL,
    amenities TEXT[] NOT NULL DEFAULT '{}',          -- PARKING / WHEELCHAIR / FOOD / M_TICKET
    cancellation_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',     -- TheatreStatus enum
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_theatre_slug ON theatres (city_id, slug);

CREATE TABLE screens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    theatre_id BIGINT NOT NULL REFERENCES theatres(id),
    name VARCHAR(50) NOT NULL,                        -- "Audi 3"
    screen_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD',   -- ScreenType enum
    sound VARCHAR(20) NULL,
    total_seats INT NOT NULL,
    layout_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_screen UNIQUE (theatre_id, name)
);

CREATE TABLE screen_seats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    screen_id BIGINT NOT NULL REFERENCES screens(id),
    seat_no VARCHAR(5) NOT NULL,                      -- "H7"
    row_label VARCHAR(2) NOT NULL,
    col_no INT NOT NULL,
    grid_row INT NOT NULL,                            -- position on the drawn map, so gaps and aisles show
    grid_col INT NOT NULL,
    category VARCHAR(20) NOT NULL,                    -- SeatCategory enum
    seat_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',  -- SeatType enum
    is_active BOOLEAN NOT NULL DEFAULT TRUE,          -- false = broken seat
    CONSTRAINT ux_screen_seat UNIQUE (screen_id, seat_no),
    CONSTRAINT ux_screen_grid UNIQUE (screen_id, grid_row, grid_col)
);

CREATE TABLE movies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug VARCHAR(150) NOT NULL,
    title VARCHAR(200) NOT NULL,
    tagline VARCHAR(200) NULL,
    synopsis TEXT NOT NULL,
    certification VARCHAR(5) NOT NULL,                -- Certification enum
    duration_min INT NOT NULL,
    release_date DATE NOT NULL,
    languages TEXT[] NOT NULL,
    formats TEXT[] NOT NULL DEFAULT '{2D}',
    genres TEXT[] NOT NULL,
    cast_members JSONB NOT NULL DEFAULT '[]',         -- [{name, role: ACTOR/DIRECTOR/MUSIC, character}]
    poster_url VARCHAR(512) NULL,                     -- null: the web app draws a poster
    banner_url VARCHAR(512) NULL,
    trailer_url VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMING_SOON',   -- MovieStatus enum
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_movie_duration CHECK (duration_min > 0)
);
CREATE UNIQUE INDEX ux_movie_slug ON movies (slug);

CREATE TABLE shows (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id),
    screen_id BIGINT NOT NULL REFERENCES screens(id),
    language VARCHAR(30) NOT NULL,
    format VARCHAR(10) NOT NULL DEFAULT '2D',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,                     -- start + runtime + ads/cleaning
    booking_opens_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',  -- ShowStatus enum; 'SCHEDULED' is used by ex_screen_overlap, never rename it
    cancel_reason VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_show_time CHECK (ends_at > starts_at AND booking_opens_at <= starts_at),
    -- No two scheduled shows overlap on one screen
    CONSTRAINT ex_screen_overlap EXCLUDE USING gist (screen_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (status = 'SCHEDULED')
);
CREATE INDEX idx_shows_movie_starts ON shows (movie_id, starts_at);

CREATE TABLE show_prices (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,                    -- SeatCategory enum
    price_paise BIGINT NOT NULL,                      -- GST-inclusive ticket price shown to users
    CONSTRAINT ux_show_price UNIQUE (show_id, category),
    CONSTRAINT ck_price CHECK (price_paise >= 0)
);

CREATE TABLE show_seats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    screen_seat_id BIGINT NOT NULL REFERENCES screen_seats(id),
    seat_no VARCHAR(5) NOT NULL,
    category VARCHAR(20) NOT NULL,                    -- SeatCategory enum, copied for fast queries
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',  -- SeatStatus enum; 'BOOKED' is used by ck_seat_booked, never rename it
    booking_id BIGINT NULL,                           -- FK to bookings is added by the booking module's migration
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT ux_show_seat UNIQUE (show_id, seat_no),
    CONSTRAINT ck_seat_booked CHECK ((status = 'BOOKED') = (booking_id IS NOT NULL))
);
