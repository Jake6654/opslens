FROM python:3.12-slim


RUN apt-get update \
    # need Java to run "./gradlew test" in Python env
    && apt-get install -y --no-install-recommends openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*


WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000

# Commands to use when the container starts
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]