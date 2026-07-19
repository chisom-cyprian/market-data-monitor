"""
visualize.py

Reads the console output produced by MarketDataMonitor (lines like
"Added data point: price=..., timestamp=...") and plots price over time.

Usage:
    Paste your MarketDataMonitor output into `java_output` below, then run:
        python3 visualize.py
"""

import re
import pandas as pd
import matplotlib.pyplot as plt

# Paste your MarketDataMonitor output between the triple quotes below
java_output = """
Added data point: price=506.049988, timestamp=2026-05-26T10:28:36.547871559Z
Added data point: price=506.120003, timestamp=2026-05-26T10:28:51.602341112Z
Added data point: price=505.980011, timestamp=2026-05-26T10:29:06.658902341Z
Added data point: price=506.350006, timestamp=2026-05-26T10:29:21.714225871Z
Added data point: price=506.500000, timestamp=2026-05-26T10:29:36.770108003Z
Added data point: price=506.279999, timestamp=2026-05-26T10:29:51.825447651Z
Added data point: price=506.610001, timestamp=2026-05-26T10:30:06.881903215Z
Added data point: price=506.899994, timestamp=2026-05-26T10:30:21.937772004Z
"""


def parse_data_points(raw_output: str) -> pd.DataFrame:
    """Extract (price, timestamp) pairs from raw monitor output into a DataFrame."""
    matches = re.findall(
        r"price=([0-9.]+), timestamp=([^\n]+)",
        raw_output
    )
    df = pd.DataFrame(matches, columns=["price", "timestamp"])
    df["price"] = df["price"].astype(float)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    return df


def plot_price_over_time(df: pd.DataFrame, symbol: str = "DIA") -> None:
    """Render a line graph of price vs. timestamp."""
    plt.figure(figsize=(10, 5))
    plt.plot(df["timestamp"], df["price"], marker="o")

    plt.title(f"{symbol} Price Over Time")
    plt.xlabel("Timestamp")
    plt.ylabel("Price")

    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    df = parse_data_points(java_output)
    print(df)
    plot_price_over_time(df)
